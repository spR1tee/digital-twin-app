package hu.digital_twin.service.infrastructure;

import hu.digital_twin.builder.VmBuilder;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.digital_twin.service.util.ResourceCalculationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Service;

/**
 * Virtuális gépek létrehozásáért felelős gyártó osztály
 */
@Service
public class VirtualMachineFactory {

    private final ResourceCalculationService resourceCalculationService;

    public VirtualMachineFactory(ResourceCalculationService resourceCalculationService) {
        this.resourceCalculationService = resourceCalculationService;
    }

    /**
     * Virtuális gépek létrehozása a szimulációs kontextusban
     */
    public void createVirtualMachines(RequestData requestData, SimulationContext context) throws Exception {
        for (VmData vmData : requestData.getVmData()) {
            createVirtualMachine(vmData, context);
            calculateAndStoreVmMetrics(vmData, context);
        }
    }

    /**
     * Backup virtuális gép létrehozása
     */
    public VirtualMachine createBackupVirtualMachine(String vmId, RequestData requestData, SimulationContext context) throws Exception {
        for (VmData vmData : requestData.getVmData()) {
            if (vmData.getName().equals(vmId)) {
                return new VmBuilder()
                        .withName(vmData.getName() + "_backup")
                        .withStartupProcess(0)
                        .withNetworkTraffic(vmData.getNetworkTraffic())
                        .withDisk(vmData.getReqDisk())
                        .withResources(vmData.getCpu(), vmData.getCoreProcessingPower(), vmData.getRam())
                        .build(context);
            }
        }
        throw new IllegalArgumentException("VM not found with ID: " + vmId);
    }

    private void createVirtualMachine(VmData vmData, SimulationContext context) throws Exception {
        new VmBuilder()
                .withName(vmData.getName())
                .withStartupProcess(vmData.getStartupProcess())
                .withNetworkTraffic(vmData.getNetworkTraffic())
                .withDisk(vmData.getReqDisk())
                .withResources(vmData.getCpu(), vmData.getCoreProcessingPower(), vmData.getRam())
                .build(context);
    }

    private void calculateAndStoreVmMetrics(VmData vmData, SimulationContext context) {
        long maxInstr = resourceCalculationService.calculateMaxInstructionsPerSecond(
                vmData.getCpu(), vmData.getCoreProcessingPower());

        context.getMaxInstrPerSecond().put(vmData.getName(), maxInstr);
        context.getFileSizes().put(vmData.getName(), vmData.getDataSinceLastSave());
    }
}
