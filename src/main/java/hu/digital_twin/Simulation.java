package hu.digital_twin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

    private int combinedData = 0;

    private double taskNumber = 0;

    private double threshold = 0;

    public Simulation() {
    }

    public String doBaseline(RequestData currentRequestData) {
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        int numberOfVms = 0;
        try {
            IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);

            final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

            Repository pmRepo1 = new Repository(107_374_182_400L, "pmRepo1", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm1 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo1, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            Repository pmRepo2 = new Repository(107_374_182_400L, "pmRepo2", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm2 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo2, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            pmRepo1.setState(NetworkNode.State.RUNNING);
            pmRepo2.setState(NetworkNode.State.RUNNING);

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
            System.out.println(lastUpdateData);
            for (VmData vd : lastUpdateData.getVmData()) {
                System.out.println(vd);
            }
            //Map<String, List<Double>> predictionData = prediction(currentRequestData);

            for (VmData vd : lastUpdateData.getVmData()) {
                VirtualAppliance va = new VirtualAppliance(vd.getName(), vd.getStartupProcess(), vd.getNetworkTraffic(), false, vd.getReqDisk());
                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());
                iaas.repositories.get(0).registerObject(va);
                iaas.requestVM(va, arc, iaas.repositories.get(0), 1);
            }

            //Starting the VMs
            Timed.simulateUntilLastEvent();
            long starttime = Timed.getFireCount();
            System.out.println("starttime: " + starttime);

            new EnergyDataCollector("pm-1", pm1, true);
            new EnergyDataCollector("pm-2", pm2, true);

            for (PhysicalMachine pm : iaas.machines) {
                System.out.println(pm);
                for (VirtualMachine vm : pm.listVMs()) {
                    numberOfVms++;
                    System.out.println("\t" + vm);
                    for (VmData vd : lastUpdateData.getVmData()) {
                        if (vd.getName().equals(vm.getVa().id)) {
                            vm.newComputeTask(100_000, vd.getUsage(), new ConsumptionEventAdapter() {
                                @Override
                                public void conComplete() {
                                    if (pm1.listVMs().contains(vm)) {
                                        int filesize = 600;
                                        try {
                                            new Transfer(pmRepo1, pmRepo2, new StorageObject("data", filesize, false));
                                            totalMovedData += filesize;
                                        } catch (NetworkNode.NetworkException ex) {
                                            throw new RuntimeException(ex);
                                        }
                                    } else {
                                        int filesize = 600;
                                        try {
                                            new Transfer(pmRepo2, pmRepo1, new StorageObject("data", filesize, false));
                                            totalMovedData += filesize;
                                        } catch (NetworkNode.NetworkException ex) {
                                            throw new RuntimeException(ex);
                                        }
                                    }
                                    System.out.println("Completed_Time: " + Timed.getFireCount());
                                    for (EnergyDataCollector edc : EnergyDataCollector.energyCollectors) {
                                        totalEnergyConsumption += edc.energyConsumption / 1000 / 3_600_000;
                                        edc.stop();
                                    }
                                }
                            });
                        }
                    }
                }
            }
            Timed.simulateUntil(Timed.getFireCount() + (60 * 60 * 1000)); // 1 hour
            //Timed.simulateUntilLastEvent();
            long stoptime = Timed.getFireCount();
            System.out.println(stoptime);

            EnergyDataCollector.energyCollectors.clear();

            long runtime = stoptime - starttime;
            String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms);
            EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
            Timed.resetTimed();

            return stats;


        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException |
                 VMManager.VMManagementException e) {
            e.printStackTrace();
        } catch (NetworkNode.NetworkException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public String doAlternative(String mode, RequestData currentRequestData) {
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
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
                    System.out.println(pm);
                    for (VirtualMachine vm : pm.listVMs()) {
                        System.out.println("\t" + vm);
                        vm.newComputeTask((double) 100_000 / numberOfVms, usage / numberOfVms, new ConsumptionEventAdapter() {
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
                                for (EnergyDataCollector edc : EnergyDataCollector.energyCollectors) {
                                    totalEnergyConsumption += edc.energyConsumption / 1000 / 3_600_000;
                                    edc.stop();
                                }
                            }
                        });
                    }
                }

                Timed.simulateUntil(Timed.getFireCount() + (60 * 60 * 1000)); // 1 hour
                //Timed.simulateUntilLastEvent();

                long stoptime = Timed.getFireCount();
                System.out.println(stoptime);

                EnergyDataCollector.energyCollectors.clear();

                long runtime = stoptime - starttime;
                String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms);
                System.out.println(stats);
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
        double correctedEnergyConsumption = totalEnergyConsumption / 2.0;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            Map<String, Object> statsMap = new LinkedHashMap<>();
            statsMap.put("runtime_ms", runtime);
            statsMap.put("runtime_minutes", minutes);
            statsMap.put("runtime_hours", hours);
            statsMap.put("total_iot_cost_usd", iotCost);
            statsMap.put("total_energy_consumption_kwh", correctedEnergyConsumption);
            statsMap.put("total_moved_data_mb", totalMovedData);
            statsMap.put("number_of_vms", vms);

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

    public Map<String, List<Double>> prediction(RequestData requestData) {
        try {
            String scriptPath = "src/main/resources/scripts/linear_regression_model.py";
            ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, requestData.getFeatureName(), Integer.toString(requestData.getBasedOnLast() * 12), Integer.toString(requestData.getPredictionLength() * 60), Integer.toString(requestData.getVmsCount()));
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


