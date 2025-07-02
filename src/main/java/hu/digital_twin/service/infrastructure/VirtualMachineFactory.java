package hu.digital_twin.service.infrastructure;

import hu.digital_twin.builder.VmBuilder;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.digital_twin.service.util.ResourceCalculationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Service;

/**
 * Szolgáltatás, amely felelős a virtuális gépek (VM-ek) létrehozásáért a szimuláció során.
 */
@Service
public class VirtualMachineFactory {

    private final ResourceCalculationService resourceCalculationService;

    public VirtualMachineFactory(ResourceCalculationService resourceCalculationService) {
        this.resourceCalculationService = resourceCalculationService;
    }

    /**
     * Virtuális gépek létrehozása az adott kérés (RequestData) alapján.
     *
     * @param requestData a VM konfigurációs adatait tartalmazza
     * @param context a szimulációs környezet, amibe a VM-ek kerülnek
     * @throws Exception ha a VM építése sikertelen
     */
    public void createVirtualMachines(RequestData requestData, SimulationContext context) throws Exception {
        for (VmData vmData : requestData.getVmData()) {
            createVirtualMachine(vmData, context);
            calculateAndStoreVmMetrics(vmData, context);
        }
    }

    /**
     * Backup virtuális gép létrehozása egy adott VM azonosító alapján.
     * A backup VM az eredeti VM adatait veszi alapul, de például startupProcess 0.
     *
     * @param vmId a backup VM alapjául szolgáló VM neve
     * @param requestData a kérés, amiben megtalálható az eredeti VM adata
     * @param context a szimulációs kontextus
     * @return az elkészült backup VM példány
     * @throws Exception ha a VM létrehozása nem sikerül vagy a vmId nem található
     */
    public VirtualMachine createBackupVirtualMachine(String vmId, RequestData requestData, SimulationContext context) throws Exception {
        for (VmData vmData : requestData.getVmData()) {
            if (vmData.getName().equals(vmId)) {
                return new VmBuilder()
                        .withName(vmData.getName() + "_backup")
                        .withStartupProcess(0)  // Backup VM gyors indítása
                        .withNetworkTraffic(vmData.getNetworkTraffic())
                        .withDisk(vmData.getReqDisk())
                        .withResources(vmData.getCpu(), vmData.getCoreProcessingPower(), vmData.getRam())
                        .build(context);
            }
        }
        throw new IllegalArgumentException("VM not found with ID: " + vmId);
    }

    /**
     * Egyedi virtuális gép létrehozása a megadott VM adatok alapján.
     */
    private void createVirtualMachine(VmData vmData, SimulationContext context) throws Exception {
        new VmBuilder()
                .withName(vmData.getName())
                .withStartupProcess(vmData.getStartupProcess())
                .withNetworkTraffic(vmData.getNetworkTraffic())
                .withDisk(vmData.getReqDisk())
                .withResources(vmData.getCpu(), vmData.getCoreProcessingPower(), vmData.getRam())
                .build(context);
    }

    /**
     * Kiszámolja egy VM maximális másodpercenkénti utasításszámát és eltárolja a szimulációs kontextusban.
     */
    private void calculateAndStoreVmMetrics(VmData vmData, SimulationContext context) {
        long maxInstr = resourceCalculationService.calculateMaxInstructionsPerSecond(
                vmData.getCpu(), vmData.getCoreProcessingPower());

        context.getMaxInstrPerSecond().put(vmData.getName(), maxInstr);
        context.getFileSizes().put(vmData.getName(), vmData.getDataSinceLastSave());
    }
}
