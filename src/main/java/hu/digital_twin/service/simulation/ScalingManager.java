package hu.digital_twin.service.simulation;

import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.infrastructure.VirtualMachineFactory;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VM skálázásért felelős szolgáltatás
 */
@Service
public class ScalingManager {

    private final VirtualMachineFactory vmFactory;
    private final TaskExecutor taskExecutor;

    public ScalingManager(VirtualMachineFactory vmFactory, TaskExecutor taskExecutor) {
        this.vmFactory = vmFactory;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Skálázott feladatok végrehajtása
     */
    public int executeScalingTasks(SimulationContext context, ProcessedPredictionData predictionData,
                                   RequestData lastUpdateData, Map<String, VirtualMachine> backUpVms,
                                   double loadThreshold) throws Exception {
        int lastBackupCreationMinute = 0;

        for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
            List<VirtualMachine> vms = new ArrayList<>(pm.listVMs());

            for (VirtualMachine vm : vms) {
                context.incrementVmCount();
                lastBackupCreationMinute = Math.max(lastBackupCreationMinute,
                        processVmTasks(vm, predictionData, context, lastUpdateData, backUpVms, loadThreshold));
            }
        }

        return lastBackupCreationMinute;
    }

    private int processVmTasks(VirtualMachine vm, ProcessedPredictionData predictionData,
                               SimulationContext context, RequestData lastUpdateData,
                               Map<String, VirtualMachine> backUpVms, double loadThreshold) throws Exception {
        String vmId = vm.getVa().id;
        List<Double> loads = predictionData.getAvgLoadsPerMinute().get(vmId);
        List<Integer> tasks = predictionData.getTaskInstructionsPerMinute().get(vmId);

        if (loads == null || tasks == null) {
            return 0;
        }

        int lastBackupCreationMinute = 0;
        for (int i = 0; i < loads.size(); i++) {
            if (loads.get(i) >= loadThreshold) {
                lastBackupCreationMinute = Math.max(lastBackupCreationMinute,
                        handleHighLoad(vm, vmId, loads.get(i), tasks.get(i), context,
                                lastUpdateData, backUpVms, i));
            } else {
                handleNormalLoad(vm, vmId, loads.get(i), tasks.get(i), context);
            }
        }

        return lastBackupCreationMinute;
    }

    private int handleHighLoad(VirtualMachine vm, String vmId, double load, int task,
                               SimulationContext context, RequestData lastUpdateData,
                               Map<String, VirtualMachine> backUpVms, int minute) throws Exception {
        if (backUpVms.containsKey(vmId)) {
            taskExecutor.distributeTaskBetweenVms(vm, backUpVms.get(vmId), load, task, context, vmId);
            return 0;
        } else {
            createAndRegisterBackupVm(vmId, lastUpdateData, context, backUpVms);
            simulateUntilMinute(minute);
            taskExecutor.distributeTaskBetweenVms(vm, backUpVms.get(vmId), load, task, context, vmId);
            return minute;
        }
    }

    private void handleNormalLoad(VirtualMachine vm, String vmId, double load, int task,
                                  SimulationContext context) throws Exception {
        taskExecutor.executeNormalTask(vm, vmId, load, task, context);
    }

    private void createAndRegisterBackupVm(String vmId, RequestData lastUpdateData,
                                           SimulationContext context, Map<String, VirtualMachine> backUpVms) throws Exception {
        VirtualMachine backupVm = vmFactory.createBackupVirtualMachine(vmId, lastUpdateData, context);
        backUpVms.put(vmId, backupVm);
        context.incrementVmCount();
    }

    private void simulateUntilMinute(int minute) {
        Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * minute));
    }
}
