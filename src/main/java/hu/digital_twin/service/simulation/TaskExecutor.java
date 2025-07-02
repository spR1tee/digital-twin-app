package hu.digital_twin.service.simulation;

import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.event.DataTransferEventHandler;
import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Feladatok végrehajtásáért felelős szolgáltatás
 */
@Service
public class TaskExecutor {

    /**
     * Nem skálázott feladatokat hajt végre egy adott VM-re
     */
    public void executeNonScalingTasks(VirtualMachine vm, ProcessedPredictionData predictionData,
                                       SimulationContext context) throws NetworkNode.NetworkException {
        String vmId = vm.getVa().id;
        List<Double> loads = predictionData.getAvgLoadsPerMinute().get(vmId);
        List<Integer> tasks = predictionData.getTaskInstructionsPerMinute().get(vmId);

        if (loads != null && tasks != null) {
            for (int i = 0; i < loads.size(); i++) {
                executeTask(vm, loads.get(i), tasks.get(i), context, vmId);
            }
        }
    }

    /**
     * Baseline feladatok végrehajtása egy VM-en
     */
    public void executeBaselineTasks(VirtualMachine vm, RequestData lastUpdateData,
                                     SimulationContext context, int predictionLength) throws NetworkNode.NetworkException {
        VmData vmData = findVmDataByName(vm.getVa().id, lastUpdateData);
        if (vmData == null) return;

        for (int i = 0; i < predictionLength; i++) {
            int instructions = calculateBaselineInstructions(vmData, context);
            context.getMetrics().addTasks(instructions);

            vm.newComputeTask(instructions, 1 - vmData.getUsage(),
                    createDataTransferHandler(context, vm, vmData.getName()));
        }
    }

    /**
     * Normál terhelésű VM-re egy feladat hozzárendelése
     */
    public void executeNormalTask(VirtualMachine vm, String vmId, double load, int task,
                                  SimulationContext context) throws NetworkNode.NetworkException {
        executeTask(vm, load, task, context, vmId);
    }

    /**
     * Feladat elosztása egy VM és egy backup VM között
     */
    public void distributeTaskBetweenVms(VirtualMachine originalVm, VirtualMachine backupVm,
                                         double load, int task, SimulationContext context,
                                         String vmId) throws NetworkNode.NetworkException {
        double halfLoad = Math.round(load * SimulationConstants.LOAD_SPLIT_RATIO);
        long halfTask = Math.round(task * SimulationConstants.LOAD_SPLIT_RATIO);

        originalVm.newComputeTask(halfTask, 1 - halfLoad,
                createDataTransferHandler(context, originalVm, vmId));

        backupVm.newComputeTask(halfTask, 1 - halfLoad,
                createDataTransferHandler(context, backupVm, vmId));

        context.getMetrics().addTasks(task);
    }

    private void executeTask(VirtualMachine vm, double load, int task, SimulationContext context, String vmId)
            throws NetworkNode.NetworkException {
        double normalizedLoad = Math.max(SimulationConstants.MIN_LOAD,
                Math.min(SimulationConstants.MAX_LOAD, load));

        vm.newComputeTask(task, 1 - normalizedLoad,
                createDataTransferHandler(context, vm, vmId));

        context.getMetrics().addTasks(task);
    }

    private VmData findVmDataByName(String vmName, RequestData requestData) {
        return requestData.getVmData().stream()
                .filter(vd -> vd.getName().equals(vmName))
                .findFirst()
                .orElse(null);
    }

    private int calculateBaselineInstructions(VmData vmData, SimulationContext context) {
        return (int) Math.round(SimulationConstants.SECONDS_PER_MINUTE *
                (vmData.getUsage() / 100.0) * context.getMaxInstrPerSecond().get(vmData.getName()));
    }

    private DataTransferEventHandler createDataTransferHandler(SimulationContext context, VirtualMachine vm, String vmId) {
        return new DataTransferEventHandler(
                context.getIaasContext(),
                vm,
                context.getFileSizes().get(vmId),
                () -> context.getMetrics().addMovedData(context.getFileSizes().get(vmId))
        );
    }
}
