package hu.digital_twin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hu.digital_twin.context.TenantContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.digital_twin.service.RequestDataService;
import hu.digital_twin.service.Transfer;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Component
@Scope("prototype")
public class Simulation {
    @Autowired
    private RequestDataService requestDataService;

    private double totalEnergyConsumption = 0.0;

    private int totalMovedData = 0;

    private int totalTasks = 0;

    private int combinedData = 0;

    private static final int SECONDS_PER_READING = 5;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int READINGS_PER_MINUTE = SECONDS_PER_MINUTE / SECONDS_PER_READING;

    public Simulation() {
    }

    public static class IaaSContext {
        public IaaSService iaas;
        public List<PhysicalMachine> pms = new ArrayList<>();
        public List<Repository> pmRepos = new ArrayList<>();
        public Repository cloudRepo;

        private final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions;


        public IaaSContext(IaaSService iaas,
                           Repository cloudRepo,
                           EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions) {
            this.iaas = iaas;
            this.cloudRepo = cloudRepo;
            this.transitions = transitions;
        }

        public void addPhysicalMachines(int count) throws NetworkNode.NetworkException {
            for (int i = 0; i < count; i++) {
                int index = pms.size() + 1;
                String repoName = "pmRepo" + index;

                Repository repo = new Repository(
                        107_374_182_400L, repoName,
                        12_500, 12_500, 12_500,
                        new HashMap<>(),
                        transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                        transitions.get(PowerTransitionGenerator.PowerStateKind.network)
                );
                repo.setState(NetworkNode.State.RUNNING);

                PhysicalMachine pm = new PhysicalMachine(
                        8, 1, 17_179_869_184L,
                        repo, 0, 10_000,
                        transitions.get(PowerTransitionGenerator.PowerStateKind.host)
                );

                iaas.registerHost(pm);
                pms.add(pm);
                pmRepos.add(repo);

                if (cloudRepo != null) {
                    cloudRepo.addLatencies(repoName, 100);
                    repo.addLatencies("cloudRepo", 100);
                }

                for (Repository other : pmRepos) {
                    if (!other.getName().equals(repo.getName())) {
                        repo.addLatencies(other.getName(), 100);
                        other.addLatencies(repo.getName(), 100);
                    }
                }
            }
        }
    }

    public IaaSContext initializeIaaS(int initialPMCount) throws Exception {
        IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);

        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

        Repository cloudRepo = new Repository(
                107_374_182_400L, "cloudRepo",
                12_500, 12_500, 12_500,
                new HashMap<>(),
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)
        );

        cloudRepo.setState(NetworkNode.State.RUNNING);
        iaas.registerRepository(cloudRepo);

        IaaSContext context = new IaaSContext(iaas, cloudRepo, transitions);
        context.addPhysicalMachines(initialPMCount);

        return context;
    }

    public class DataTransferOnConsumption extends ConsumptionEventAdapter {

        private final VirtualMachine vm;
        private final IaaSContext context;
        private final int fileSize;

        public DataTransferOnConsumption(IaaSContext context, VirtualMachine vm, int fileSize) {
            this.context = context;
            this.vm = vm;
            this.fileSize = fileSize;
        }

        @Override
        public void conComplete() {
            Repository source = null;
            Repository target = null;

            for (int i = 0; i < context.pms.size(); i++) {
                if (context.pms.get(i).listVMs().contains(vm)) {
                    source = context.pmRepos.get(i);

                    for (int j = 0; j < context.pmRepos.size(); j++) {
                        if (j != i) {
                            target = context.pmRepos.get(j);
                            break;
                        }
                    }
                    break;
                }
            }

            try {
                String fileName = "data";
                new Transfer(source, target, new StorageObject(fileName, fileSize, false));
                totalMovedData += fileSize;
            } catch (NetworkNode.NetworkException ex) {
                throw new RuntimeException("Transfer failed", ex);
            }
        }
    }

    public void setupEDC(IaaSContext context) {
        int index = 1;
        for (PhysicalMachine pm : context.iaas.machines) {
            String name = "pm-" + index++;
            new EnergyDataCollector(name, pm, true);
        }
    }

    public void stopEDC() {
        for (EnergyDataCollector edc : EnergyDataCollector.energyCollectors) {
            totalEnergyConsumption += edc.energyConsumption / 1000 / 3_600_000;
            edc.stop();
        }
        EnergyDataCollector.energyCollectors.clear();
    }


    public String usePrediction(RequestData currentRequestData) {
        Map<String, VirtualMachine> backUpVms = new HashMap<>();
        double loadThreshold = 0.8;
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
        int numberOfVms = 0;

        try {
            IaaSContext context = initializeIaaS(2);
            context.addPhysicalMachines(1);
            RequestData lastUpdateData = requestDataService.getLastData();
            Map<String, Long> maxInstrPerSecond = new HashMap<>();

            for (VmData vd : lastUpdateData.getVmData()) {
                VirtualAppliance va = new VirtualAppliance(vd.getName(), vd.getStartupProcess(), vd.getNetworkTraffic(), false, vd.getReqDisk());
                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());
                context.iaas.repositories.get(0).registerObject(va);
                context.iaas.requestVM(va, arc, context.iaas.repositories.get(0), 1);
                long maxInstr = calculateMaxInstructionsPerSecond(vd.getCpu(), vd.getCoreProcessingPower());
                maxInstrPerSecond.put(vd.getName(), maxInstr);
            }

            Map<String, List<Double>> predictionData = prediction(currentRequestData);
            Map<String, List<Double>> avgLoadsPerMinute = new HashMap<>();
            Map<String, List<Long>> taskInstructionsPerMinute = new HashMap<>();

            for (Map.Entry<String, List<Double>> entry : predictionData.entrySet()) {
                String vmId = entry.getKey();
                List<Double> loadValues = entry.getValue();

                List<Double> avgLoads = new ArrayList<>();
                List<Long> instructions = new ArrayList<>();

                for (int i = 0; i < loadValues.size(); i += READINGS_PER_MINUTE) {
                    int endIdx = Math.min(i + READINGS_PER_MINUTE, loadValues.size());
                    List<Double> window = loadValues.subList(i, endIdx);

                    double avg = window.stream().mapToDouble(d -> d).average().orElse(0.0);
                    long instr = Math.round(SECONDS_PER_MINUTE * (avg / 100.0) * maxInstrPerSecond.get(vmId));

                    avgLoads.add(avg);
                    instructions.add(instr);
                }

                avgLoadsPerMinute.put(vmId, avgLoads);
                taskInstructionsPerMinute.put(vmId, instructions);
            }

            Timed.simulateUntilLastEvent();
            long starttime = Timed.getFireCount();
            setupEDC(context);

            for (PhysicalMachine pm : context.iaas.machines) {
                for (VirtualMachine vm : pm.listVMs()) {
                    numberOfVms++;
                    for (Map.Entry<String, List<Double>> entry : avgLoadsPerMinute.entrySet()) {
                        String vmId = entry.getKey();
                        if (vm.getVa().id.equals(vmId)) {
                            List<Double> loads = avgLoadsPerMinute.get(vmId);
                            List<Long> tasks = taskInstructionsPerMinute.get(vmId);
                            for (int i = 0; i < loads.size(); i++) {
                                totalTasks += tasks.get(i);
                                if (loads.get(i) >= loadThreshold) {
                                    if (backUpVms.containsKey(vmId)) {
                                        vm.newComputeTask((double) tasks.get(i) / 2, loads.get(i) / 2, new DataTransferOnConsumption(context, vm, 600));
                                        backUpVms.get(vmId).newComputeTask((double) tasks.get(i) / 2, loads.get(i) / 2, new DataTransferOnConsumption(context, backUpVms.get(vmId), 600));
                                    } else {
                                        for (VmData vd : lastUpdateData.getVmData()) {
                                            if (vd.getName().equals(vmId)) {
                                                VirtualAppliance va = new VirtualAppliance(vd.getName() + "back_up", 0, vd.getNetworkTraffic(), false, vd.getReqDisk());
                                                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());
                                                context.iaas.repositories.get(0).registerObject(va);
                                                VirtualMachine backUp = context.iaas.requestVM(va, arc, context.iaas.repositories.get(0), 1)[0];
                                                backUpVms.put(vmId, backUp);
                                                numberOfVms++;
                                                System.out.println(backUp.getState());
                                                vm.newComputeTask((double) tasks.get(i) / 2, loads.get(i) / 2, new DataTransferOnConsumption(context, vm, 600));
                                                backUpVms.get(vmId).newComputeTask((double) tasks.get(i) / 2, loads.get(i) / 2, new DataTransferOnConsumption(context, backUpVms.get(vmId), 600));
                                            }
                                        }
                                    }
                                } else {
                                    vm.newComputeTask(tasks.get(i), loads.get(i), new DataTransferOnConsumption(context, vm, 600));
                                }
                            }
                        }
                    }
                }
            }

            Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * currentRequestData.getPredictionLength()));

            stopEDC();

            long stoptime = Timed.getFireCount();
            long runtime = stoptime - starttime;
            String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms);
            System.out.println("TotalTasks done: " + totalTasks);
            EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
            Timed.resetTimed();

            return stats;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String doBaseline(RequestData currentRequestData) {
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
        int numberOfVms = 0;

        try {
            IaaSContext context = initializeIaaS(2);

            RequestData lastUpdateData = requestDataService.getLastData();

            Map<String, Long> maxInstrPerSecond = new HashMap<>();
            for (VmData vd : lastUpdateData.getVmData()) {
                VirtualAppliance va = new VirtualAppliance(vd.getName(), vd.getStartupProcess(), vd.getNetworkTraffic(), false, vd.getReqDisk());
                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());
                context.iaas.repositories.get(0).registerObject(va);
                context.iaas.requestVM(va, arc, context.iaas.repositories.get(0), 1);
                long maxInstr = calculateMaxInstructionsPerSecond(vd.getCpu(), vd.getCoreProcessingPower());
                maxInstrPerSecond.put(vd.getName(), maxInstr);
            }

            //Starting the VMs
            Timed.simulateUntilLastEvent();
            long starttime = Timed.getFireCount();
            setupEDC(context);

            for (PhysicalMachine pm : context.iaas.machines) {
                for (VirtualMachine vm : pm.listVMs()) {
                    numberOfVms++;
                    for (VmData vd : lastUpdateData.getVmData()) {
                        if (vd.getName().equals(vm.getVa().id)) {
                            for (int i = 0; i < currentRequestData.getPredictionLength(); i++) {
                                long instr = Math.round(SECONDS_PER_MINUTE * (vd.getUsage() / 100.0) * maxInstrPerSecond.get(vd.getName()));
                                totalTasks += (int) instr;
                                vm.newComputeTask(instr, vd.getUsage(), new DataTransferOnConsumption(context, vm, 600));
                            }
                        }
                    }
                }
            }

            Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * currentRequestData.getPredictionLength()));

            stopEDC();

            long stoptime = Timed.getFireCount();
            long runtime = stoptime - starttime;
            String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms);
            System.out.println("TotalTasks done: " + totalTasks);
            EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
            Timed.resetTimed();

            return stats;


        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException |
                 VMManager.VMManagementException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public String doAlternative(String mode, RequestData currentRequestData) {
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
        try {
            IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);

            final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

            Repository pmRepo1 = new Repository(107_374_182_400L, "pmRepo1", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm1 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo1, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            Repository pmRepo2 = new Repository(107_374_182_400L, "pmRepo2", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm2 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo2, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            iaas.registerHost(pm1);
            iaas.registerHost(pm2);

            Repository cloudRepo = new Repository(107_374_182_400L, "cloudRepo", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            iaas.registerRepository(cloudRepo);

            cloudRepo.addLatencies("pmRepo1", 100);
            cloudRepo.addLatencies("pmRepo2", 125);
            pmRepo1.addLatencies("cloudRepo", 100);
            pmRepo2.addLatencies("cloudRepo", 100);
            pmRepo1.addLatencies("pmRepo2", 100);
            pmRepo2.addLatencies("pmRepo1", 100);


            RequestData lastUpdateData = requestDataService.getLastData();
            double usage = 0;
            int networkTraffic = 0;
            int numberOfVms;
            if (lastUpdateData.getVmData().size() > 1) {
                if (mode.equals("down")) {
                    numberOfVms = lastUpdateData.getVmData().size() - 1;
                } else if (mode.equals("up")) {
                    numberOfVms = lastUpdateData.getVmData().size() + 1;
                } else {
                    return mode;
                }
                for (VmData vd : lastUpdateData.getVmData()) {
                    networkTraffic += vd.getNetworkTraffic();
                    usage += vd.getUsage();
                }
                VmData tmp = lastUpdateData.getVmData().get(0);
                VirtualAppliance va = new VirtualAppliance(tmp.getName(), tmp.getStartupProcess(), networkTraffic / numberOfVms, false, tmp.getReqDisk());
                AlterableResourceConstraints arc = new AlterableResourceConstraints(tmp.getCpu(), tmp.getCoreProcessingPower(), tmp.getRam());
                iaas.repositories.get(0).registerObject(va);
                iaas.requestVM(va, arc, iaas.repositories.get(0), numberOfVms);

                Timed.simulateUntilLastEvent();
                long starttime = Timed.getFireCount();
                System.out.println("starttime: " + starttime);

                new EnergyDataCollector("pm-1", pm1, true);
                new EnergyDataCollector("pm-2", pm2, true);

                for (VmData vd : lastUpdateData.getVmData()) {
                    combinedData += vd.getDataSinceLastSave();
                }

                for (PhysicalMachine pm : iaas.machines) {
                    for (VirtualMachine vm : pm.listVMs()) {
                        totalTasks += 100000 / numberOfVms;
                        vm.newComputeTask(Math.round((float) 100_000 / numberOfVms), usage / numberOfVms, new ConsumptionEventAdapter() {
                            @Override
                            public void conComplete() {
                                if (pm1.listVMs().contains(vm)) {
                                    try {
                                        int filesize = 600;
                                        new Transfer(pmRepo1, pmRepo2, new StorageObject("data", 1200 / numberOfVms, false));
                                        totalMovedData += 1200 / numberOfVms;
                                    } catch (NetworkNode.NetworkException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                } else {
                                    try {
                                        int filesize = 600;
                                        new Transfer(pmRepo2, pmRepo1, new StorageObject("data", 1200 / numberOfVms, false));
                                        totalMovedData += 1200 / numberOfVms;
                                    } catch (NetworkNode.NetworkException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }
                                System.out.println("Completed_Time: " + Timed.getFireCount());
                            }
                        });
                    }
                }
                Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * currentRequestData.getPredictionLength()));
                for (EnergyDataCollector edc : EnergyDataCollector.energyCollectors) {
                    totalEnergyConsumption += edc.energyConsumption / 1000 / 3_600_000;
                    edc.stop();
                }
                long stoptime = Timed.getFireCount();
                EnergyDataCollector.energyCollectors.clear();

                long runtime = stoptime - starttime;
                String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms);
                System.out.println(totalTasks);
                EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
                Timed.resetTimed();

                return stats;
            }

        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 VMManager.VMManagementException | NetworkNode.NetworkException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public String generateRuntimeStats(long runtime, RequestData lastUpdateData, double totalEnergyConsumption, int totalMovedData, int vms) {
        double hours = runtime / 3600000.0;
        double minutes = runtime / 60000.0;
        double iotCost = calculateIoTCost(lastUpdateData, hours);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            Map<String, Object> statsMap = new LinkedHashMap<>();
            statsMap.put("runtime_ms", runtime);
            statsMap.put("runtime_minutes", minutes);
            statsMap.put("runtime_hours", hours);
            statsMap.put("total_iot_cost_usd", iotCost);
            statsMap.put("total_energy_consumption_kwh", totalEnergyConsumption);
            statsMap.put("total_moved_data_mb", totalMovedData);
            statsMap.put("number_of_vms_utilized", vms);

            return objectMapper.writeValueAsString(statsMap);
        } catch (JsonProcessingException e) {
            return "Failed to generate JSON stats";
        }
    }

    public double calculateIoTCost(RequestData rd, double time) {
        double ramCost = 0.005;
        double cpuCost = 0.05;
        double totalCost = 0.0;

        for (VmData vd : rd.getVmData()) {
            totalCost += (vd.getRam() / (1024.0 * 1024.0 * 1024.0)) * ramCost * time;
            totalCost += vd.getCpu() * cpuCost * time;
        }
        return totalCost;
    }

    private static long calculateMaxInstructionsPerSecond(int numCores, double instrPerMsPerCore) {
        return (long) (numCores * instrPerMsPerCore * 1000); // ms -> sec
    }

    public Map<String, List<Double>> prediction(RequestData requestData) {
        try {
            String scriptPath = "src/main/resources/scripts/linear_regression_model.py";
            ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, requestData.getFeatureName(), Integer.toString(requestData.getBasedOnLast() * 12), Integer.toString(requestData.getPredictionLength() * 60), Integer.toString(requestData.getVmsCount()), TenantContext.getTenantId());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder jsonData = new StringBuilder();
            boolean jsonStarted = false;
            boolean jsonEnded = false;
            Map<String, List<Double>> predictionData = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                if (line.equals("JSON_DATA_START")) {
                    jsonStarted = true;
                    continue;
                }
                if (line.equals("JSON_DATA_END")) {
                    jsonEnded = true;
                    continue;
                }

                if (jsonStarted && !jsonEnded) {
                    jsonData.append(line);
                } else {
                    System.out.println(line);
                }
            }

            if (!jsonData.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                predictionData = objectMapper.readValue(
                        jsonData.toString(),
                        new TypeReference<Map<String, List<Double>>>() {
                        }
                );
            }

            int exitCode = process.waitFor();
            System.out.println("Python script exited with code: " + exitCode);
            return predictionData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}


