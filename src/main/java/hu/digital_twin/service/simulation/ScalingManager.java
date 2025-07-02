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
 * VM skálázásért felelős szolgáltatás.
 * Elvégzi a VM-ekre előrejelzett terhelések alapján a feladatok szétosztását.
 * Ha egy VM túlterhelt, backup VM-et hoz létre, és szétosztja a feladatokat.
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
     * Predikciós adatok alapján végrehajtja a skálázott feladatokat VM-enként.
     * @return az utolsó backup VM létrehozásának időpontját percben
     */
    public int executeScalingTasks(SimulationContext context, ProcessedPredictionData predictionData,
                                   RequestData lastUpdateData, Map<String, VirtualMachine> backUpVms,
                                   double loadThreshold) throws Exception {
        int lastBackupCreationMinute = 0;

        // Összes fizikai gép bejárása
        for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
            List<VirtualMachine> vms = new ArrayList<>(pm.listVMs());

            for (VirtualMachine vm : vms) {
                context.incrementVmCount();

                // Egy adott VM predikciós terhelésének feldolgozása
                lastBackupCreationMinute = Math.max(lastBackupCreationMinute,
                        processVmTasks(vm, predictionData, context, lastUpdateData, backUpVms, loadThreshold));
            }
        }

        return lastBackupCreationMinute;
    }

    /**
     * Egy VM-re vonatkozó predikciós adatok feldolgozása.
     * @return backup VM létrehozásának legutolsó perc indexe (ha volt)
     */
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

        // Percenkénti predikciós adatok feldolgozása
        for (int i = 0; i < loads.size(); i++) {
            if (loads.get(i) >= loadThreshold) {
                // Túlterhelés esetén backup VM létrehozása / használata
                lastBackupCreationMinute = Math.max(lastBackupCreationMinute,
                        handleHighLoad(vm, vmId, loads.get(i), tasks.get(i), context,
                                lastUpdateData, backUpVms, i));
            } else {
                // Normál terhelés esetén csak egy VM-et használ
                handleNormalLoad(vm, vmId, loads.get(i), tasks.get(i), context);
            }
        }

        return lastBackupCreationMinute;
    }

    /**
     * Túlterhelés esetén új backup VM létrehozása vagy meglévő használata,
     * majd a feladat szétosztása.
     */
    private int handleHighLoad(VirtualMachine vm, String vmId, double load, int task,
                               SimulationContext context, RequestData lastUpdateData,
                               Map<String, VirtualMachine> backUpVms, int minute) throws Exception {
        if (backUpVms.containsKey(vmId)) {
            // Már létező backup VM-et használunk
            taskExecutor.distributeTaskBetweenVms(vm, backUpVms.get(vmId), load, task, context, vmId);
            return 0;
        } else {
            // Új backup VM létrehozása
            createAndRegisterBackupVm(vmId, lastUpdateData, context, backUpVms);

            // Szimulációs idő léptetése a következő percig
            simulateUntilMinute(minute);

            // Feladat szétosztása backup és eredeti VM között
            taskExecutor.distributeTaskBetweenVms(vm, backUpVms.get(vmId), load, task, context, vmId);
            return minute;
        }
    }

    /**
     * Normál terhelés esetén a feladatot az eredeti VM-re rendeli hozzá.
     */
    private void handleNormalLoad(VirtualMachine vm, String vmId, double load, int task,
                                  SimulationContext context) throws Exception {
        taskExecutor.executeNormalTask(vm, vmId, load, task, context);
    }

    /**
     * Új backup VM létrehozása és regisztrálása.
     */
    private void createAndRegisterBackupVm(String vmId, RequestData lastUpdateData,
                                           SimulationContext context, Map<String, VirtualMachine> backUpVms) throws Exception {
        VirtualMachine backupVm = vmFactory.createBackupVirtualMachine(vmId, lastUpdateData, context);
        backUpVms.put(vmId, backupVm);
        context.incrementVmCount();
    }

    /**
     * Szimuláció időléptetése egy adott percig.
     */
    private void simulateUntilMinute(int minute) {
        Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * minute));
    }
}
