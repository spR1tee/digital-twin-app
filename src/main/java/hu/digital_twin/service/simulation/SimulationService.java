package hu.digital_twin.service.simulation;

import hu.digital_twin.builder.VmBuilder;
import hu.digital_twin.config.SimulationConfig;
import hu.digital_twin.context.IaaSContext;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.event.DataTransferEventHandler;
import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.digital_twin.service.infrastructure.EnergyService;
import hu.digital_twin.service.infrastructure.IaaSManagerService;
import hu.digital_twin.service.io.RequestDataService;
import hu.digital_twin.service.prediction.PredictionService;
import hu.digital_twin.service.util.ResourceCalculationService;
import hu.digital_twin.strategy.BaselineSimulationStrategy;
import hu.digital_twin.strategy.PredictionWithScalingStrategy;
import hu.digital_twin.strategy.PredictionWithoutScalingStrategy;
import hu.digital_twin.strategy.SimulationStrategy;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SimulationService {

    private final RequestDataService requestDataService;
    private final IaaSManagerService iaaSManagerService;
    private final EnergyService energyService;
    private final PredictionService predictionService;
    private final ResourceCalculationService resourceCalculationService;
    private final SimulationStatsService simulationStatsService;
    private final SimulationConfig config;

    private final Map<String, SimulationStrategy> strategies;
    private long starttime;
    private long stoptime;

    public SimulationService(RequestDataService requestDataService,
                             IaaSManagerService iaaSManagerService,
                             EnergyService energyService,
                             PredictionService predictionService,
                             ResourceCalculationService resourceCalculationService,
                             SimulationStatsService simulationStatsService,
                             SimulationConfig config,
                             @Lazy BaselineSimulationStrategy baselineStrategy,
                             @Lazy PredictionWithoutScalingStrategy withoutScalingStrategy,
                             @Lazy PredictionWithScalingStrategy withScalingStrategy) {
        this.requestDataService = requestDataService;
        this.iaaSManagerService = iaaSManagerService;
        this.energyService = energyService;
        this.predictionService = predictionService;
        this.resourceCalculationService = resourceCalculationService;
        this.simulationStatsService = simulationStatsService;
        this.config = config;
        this.starttime = 0;
        this.stoptime = 0;

        this.strategies = Map.of(
                "baseline", baselineStrategy,
                "prediction_no_scaling", withoutScalingStrategy,
                "prediction_with_scaling", withScalingStrategy
        );
    }

    // Publikus API metódusok
    public String usePredictionWithoutScaling(RequestData currentRequestData) throws SimulationException {
        return strategies.get("prediction_no_scaling").execute(currentRequestData);
    }

    public String usePredictionWithScaling(RequestData currentRequestData) throws SimulationException {
        return strategies.get("prediction_with_scaling").execute(currentRequestData);
    }

    public String doBaseline(RequestData currentRequestData) throws SimulationException {
        return strategies.get("baseline").execute(currentRequestData);
    }

    // Közös metódusok
    public SimulationContext initializeSimulation(int physicalMachineCount) throws Exception {
        RequestData lastUpdateData = requestDataService.getLastData();
        IaaSContext iaasContext = iaaSManagerService.initializeIaaS(physicalMachineCount);
        SimulationContext context = new SimulationContext(iaasContext);

        createVirtualMachines(lastUpdateData, context);
        return context;
    }

    private void createVirtualMachines(RequestData lastUpdateData, SimulationContext context) throws Exception {
        for (VmData vd : lastUpdateData.getVmData()) {
            new VmBuilder()
                    .withName(vd.getName())
                    .withStartupProcess(vd.getStartupProcess())
                    .withNetworkTraffic(vd.getNetworkTraffic())
                    .withDisk(vd.getReqDisk())
                    .withResources(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam())
                    .build(context);

            long maxInstr = resourceCalculationService.calculateMaxInstructionsPerSecond(
                    vd.getCpu(), vd.getCoreProcessingPower());
            context.getMaxInstrPerSecond().put(vd.getName(), maxInstr);
            context.getFileSizes().put(vd.getName(), vd.getDataSinceLastSave());
        }
    }

    public ProcessedPredictionData processPerMinuteData(Map<String, List<Double>> predictionData,
                                                        SimulationContext context) {
        Map<String, List<Double>> avgLoadsPerMinute = new HashMap<>();
        Map<String, List<Integer>> taskInstructionsPerMinute = new HashMap<>();

        predictionData.entrySet().parallelStream().forEach(entry -> {
            String vmId = entry.getKey();
            List<Double> loadValues = entry.getValue();

            List<Double> avgLoads = new ArrayList<>();
            List<Integer> instructions = new ArrayList<>();

            for (int i = 0; i < loadValues.size(); i += SimulationConstants.READINGS_PER_MINUTE) {
                int endIdx = Math.min(i + SimulationConstants.READINGS_PER_MINUTE, loadValues.size());
                List<Double> window = loadValues.subList(i, endIdx);

                double avg = window.stream().mapToDouble(d -> d).average().orElse(0.0);
                int instr = (int) Math.round(SimulationConstants.SECONDS_PER_MINUTE *
                        (avg / 100.0) * context.getMaxInstrPerSecond().get(vmId));

                avgLoads.add(avg);
                instructions.add(instr);
            }

            synchronized (avgLoadsPerMinute) {
                avgLoadsPerMinute.put(vmId, avgLoads);
                taskInstructionsPerMinute.put(vmId, instructions);
            }
        });

        return new ProcessedPredictionData(avgLoadsPerMinute, taskInstructionsPerMinute);
    }

    public void startSimulation(SimulationContext context) {
        Timed.simulateUntilLastEvent();
        setStarttime(Timed.getFireCount());
        energyService.setupEDC(context.getIaasContext());
    }

    public void runSimulation(int minutes) {
        Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * minutes));
    }

    public void executeNonScalingTasks(VirtualMachine vm, ProcessedPredictionData predictionData,
                                       SimulationContext context) throws NetworkNode.NetworkException {
        String vmId = vm.getVa().id;
        List<Double> loads = predictionData.getAvgLoadsPerMinute().get(vmId);
        List<Integer> tasks = predictionData.getTaskInstructionsPerMinute().get(vmId);

        if (loads != null && tasks != null) {
            for (int i = 0; i < loads.size(); i++) {
                double load = Math.max(SimulationConstants.MIN_LOAD,
                        Math.min(SimulationConstants.MAX_LOAD, loads.get(i)));

                vm.newComputeTask(tasks.get(i), 1 - load,
                        new DataTransferEventHandler(context.getIaasContext(), vm,
                                context.getFileSizes().get(vmId),
                                () -> context.getMetrics().addMovedData(context.getFileSizes().get(vmId))));

                context.getMetrics().addTasks(tasks.get(i));
            }
        }
    }

    public void executeBaselineTasks(VirtualMachine vm, RequestData lastUpdateData,
                                     SimulationContext context, int predictionLength) throws NetworkNode.NetworkException {
        for (VmData vd : lastUpdateData.getVmData()) {
            if (vd.getName().equals(vm.getVa().id)) {
                for (int i = 0; i < predictionLength; i++) {
                    int instr = (int) Math.round(SimulationConstants.SECONDS_PER_MINUTE *
                            (vd.getUsage() / 100.0) * context.getMaxInstrPerSecond().get(vd.getName()));

                    context.getMetrics().addTasks(instr);
                    vm.newComputeTask(instr, 1 - vd.getUsage(),
                            new DataTransferEventHandler(context.getIaasContext(), vm,
                                    context.getFileSizes().get(vm.getVa().id),
                                    () -> context.getMetrics().addMovedData(context.getFileSizes().get(vm.getVa().id))));
                }
                break;
            }
        }
    }

    public int executeScalingTasks(SimulationContext context, ProcessedPredictionData predictionData,
                                   RequestData lastUpdateData, Map<String, VirtualMachine> backUpVms,
                                   double loadThreshold) throws Exception {
        int lastBackupCreationMinute = 0;

        for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
            for (VirtualMachine vm : new ArrayList<>(pm.listVMs())) {
                context.incrementVmCount();
                String vmId = vm.getVa().id;

                List<Double> loads = predictionData.getAvgLoadsPerMinute().get(vmId);
                List<Integer> tasks = predictionData.getTaskInstructionsPerMinute().get(vmId);

                if (loads != null && tasks != null) {
                    for (int i = 0; i < loads.size(); i++) {
                        if (loads.get(i) >= loadThreshold) {
                            lastBackupCreationMinute = Math.max(lastBackupCreationMinute,
                                    handleHighLoad(vm, vmId, loads.get(i), tasks.get(i),
                                            context, lastUpdateData, backUpVms, i));
                        } else {
                            handleNormalLoad(vm, vmId, loads.get(i), tasks.get(i), context);
                        }
                    }
                }
            }
        }

        return lastBackupCreationMinute;
    }

    private int handleHighLoad(VirtualMachine vm, String vmId, double load, int task,
                               SimulationContext context, RequestData lastUpdateData,
                               Map<String, VirtualMachine> backUpVms, int minute) throws Exception {
        if (backUpVms.containsKey(vmId)) {
            distributeTaskBetweenVms(vm, backUpVms.get(vmId), load, task, context, vmId);
            return 0;
        } else {
            createBackupVm(vmId, lastUpdateData, context, backUpVms);
            Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * (minute + 1)));
            distributeTaskBetweenVms(vm, backUpVms.get(vmId), load, task, context, vmId);
            return minute + 1;
        }
    }

    private void handleNormalLoad(VirtualMachine vm, String vmId, double load, int task,
                                  SimulationContext context) throws NetworkNode.NetworkException {
        vm.newComputeTask(task, 1 - load,
                new DataTransferEventHandler(context.getIaasContext(), vm,
                        context.getFileSizes().get(vmId),
                        () -> context.getMetrics().addMovedData(context.getFileSizes().get(vmId))));
        context.getMetrics().addTasks(task);
    }

    private void distributeTaskBetweenVms(VirtualMachine originalVm, VirtualMachine backupVm,
                                          double load, int task, SimulationContext context, String vmId) throws NetworkNode.NetworkException {
        double halfLoad = Math.round(load * SimulationConstants.LOAD_SPLIT_RATIO);
        long halfTask = Math.round(task * SimulationConstants.LOAD_SPLIT_RATIO);

        originalVm.newComputeTask(halfTask, 1 - halfLoad,
                new DataTransferEventHandler(context.getIaasContext(), originalVm,
                        context.getFileSizes().get(vmId),
                        () -> context.getMetrics().addMovedData(context.getFileSizes().get(vmId))));

        backupVm.newComputeTask(halfTask, 1 - halfLoad,
                new DataTransferEventHandler(context.getIaasContext(), backupVm,
                        context.getFileSizes().get(vmId),
                        () -> context.getMetrics().addMovedData(context.getFileSizes().get(vmId))));

        context.getMetrics().addTasks(task);
    }

    private void createBackupVm(String vmId, RequestData lastUpdateData, SimulationContext context,
                                Map<String, VirtualMachine> backUpVms) throws Exception {
        for (VmData vd : lastUpdateData.getVmData()) {
            if (vd.getName().equals(vmId)) {
                VirtualMachine backupVm = new VmBuilder()
                        .withName(vd.getName() + "backup")
                        .withStartupProcess(0)
                        .withNetworkTraffic(vd.getNetworkTraffic())
                        .withDisk(vd.getReqDisk())
                        .withResources(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam())
                        .build(context);

                backUpVms.put(vmId, backupVm);
                context.incrementVmCount();
                break;
            }
        }
    }

    public String finalizeSimulation(SimulationContext context, RequestData lastUpdateData,
                                     Map<String, VirtualMachine> backUpVms) {
        setStoptime(Timed.getFireCount());
        double totalEnergyConsumption = energyService.stopEDC();
        context.getMetrics().addEnergyConsumption(totalEnergyConsumption);

        String stats = simulationStatsService.generateRuntimeStats(
                getStoptime() - getStarttime(), lastUpdateData, context.getMetrics().getTotalEnergyConsumption(),
                context.getMetrics().getTotalMovedData(), context.getNumberOfVms(),
                backUpVms, context.getMetrics().getTotalTasks());

        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
        Timed.resetTimed();

        if (backUpVms != null && !backUpVms.isEmpty()) {
            backUpVms.clear();
        }

        return stats;
    }

    public RequestDataService getRequestDataService() {
        return requestDataService;
    }

    public PredictionService getPredictionService() {
        return predictionService;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public long getStarttime() {
        return starttime;
    }

    public void setStarttime(long starttime) {
        this.starttime = starttime;
    }

    public long getStoptime() {
        return stoptime;
    }

    public void setStoptime(long stoptime) {
        this.stoptime = stoptime;
    }
}
