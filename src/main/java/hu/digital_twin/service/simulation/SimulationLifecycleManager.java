package hu.digital_twin.service.simulation;

import hu.digital_twin.context.IaaSContext;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.infrastructure.EnergyService;
import hu.digital_twin.service.infrastructure.IaaSManagerService;
import hu.digital_twin.service.infrastructure.VirtualMachineFactory;
import hu.digital_twin.service.io.RequestDataService;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * A szimuláció teljes életciklusát vezérlő szolgáltatás.
 * Felelős az infrastruktúra inicializálásáért, szimuláció elindításáért, futtatásáért, lezárásáért és az eredmények rögzítéséért.
 */
@Service
public class SimulationLifecycleManager {

    private final RequestDataService requestDataService;
    private final IaaSManagerService iaaSManagerService;
    private final EnergyService energyService;
    private final VirtualMachineFactory vmFactory;
    private final SimulationStatsService simulationStatsService;

    private long starttime = 0; // szimuláció kezdőidőpontja (szimulációs idő)
    private long stoptime = 0;  // szimuláció végidőpontja (szimulációs idő)

    public SimulationLifecycleManager(RequestDataService requestDataService,
                                      IaaSManagerService iaaSManagerService,
                                      EnergyService energyService,
                                      VirtualMachineFactory vmFactory,
                                      SimulationStatsService simulationStatsService) {
        this.requestDataService = requestDataService;
        this.iaaSManagerService = iaaSManagerService;
        this.energyService = energyService;
        this.vmFactory = vmFactory;
        this.simulationStatsService = simulationStatsService;
    }

    /**
     * Szimulációs környezet és virtuális gépek előkészítése.
     *
     * @param physicalMachineCount a fizikai gépek száma
     * @return a szimuláció kontextusa, amely tartalmazza az infrastruktúrát és VM metrikákat
     */
    public SimulationContext initializeSimulation(int physicalMachineCount) throws Exception {
        RequestData lastUpdateData = requestDataService.getLastData(); // utolsó frissítési kérés lekérése
        IaaSContext iaasContext = iaaSManagerService.initializeIaaS(physicalMachineCount); // fizikai infrastruktúra inicializálása
        SimulationContext context = new SimulationContext(iaasContext); // szimulációs kontextus létrehozása

        vmFactory.createVirtualMachines(lastUpdateData, context); // VM-ek létrehozása
        return context;
    }

    /**
     * A szimulációs események elindítása és energiafogyasztás-gyűjtő eszköz aktiválása.
     *
     * @param context a szimuláció kontextusa
     */
    public void startSimulation(SimulationContext context) {
        Timed.simulateUntilLastEvent(); // elindítja az összes eddig beütemezett esemény szimulációját
        setStarttime(Timed.getFireCount()); // menti a kezdési időpontot
        energyService.setupEDC(context.getIaasContext()); // energiafogyasztás figyelés aktiválása
    }

    /**
     * Szimuláció futtatása meghatározott percekig.
     *
     * @param minutes futtatási idő percben
     */
    public void runSimulation(int minutes) {
        // futtatja a szimulációt az aktuális szimulációs idő + megadott időintervallumig
        Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * minutes));
    }

    /**
     * Szimuláció lezárása, energiafogyasztás lekérése, statisztikák legenerálása, és erőforrások felszabadítása.
     *
     * @param context a szimuláció kontextusa
     * @param lastUpdateData utolsó frissítési adat
     * @param backUpVms mentésre létrehozott VM-ek (pl. skálázáskor)
     * @return a szimuláció eredményeit tartalmazó statisztika szöveg
     */
    public String finalizeSimulation(SimulationContext context, RequestData lastUpdateData,
                                     Map<String, VirtualMachine> backUpVms) {
        setStoptime(Timed.getFireCount()); // szimulációs idő leállítása
        double totalEnergyConsumption = energyService.stopEDC(); // energiafogyasztás összegyűjtése és EDC leállítása
        context.getMetrics().addEnergyConsumption(totalEnergyConsumption); // energia metrika elmentése

        // statisztikák legenerálása (pl. időtartam, energia, adatmozgatás, VM szám, stb.)
        String stats = simulationStatsService.generateRuntimeStats(
                getStoptime() - getStarttime(),
                lastUpdateData,
                context.getMetrics().getTotalEnergyConsumption(),
                context.getMetrics().getTotalMovedData(),
                context.getNumberOfVms(),
                backUpVms,
                context.getMetrics().getTotalTasks()
        );

        cleanupSimulation(backUpVms); // szimuláció erőforrásainak felszabadítása
        return stats;
    }

    /**
     * Szimuláció után az időkezelő és energia log mentése, backup VM-ek törlése.
     */
    private void cleanupSimulation(Map<String, VirtualMachine> backUpVms) {
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory); // energiafogyasztási log fájlba írás
        Timed.resetTimed(); // Timed singleton visszaállítása

        if (backUpVms != null && !backUpVms.isEmpty()) {
            backUpVms.clear(); // backup VM-ek eltávolítása a memóriából
        }
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
