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
 * Szimuláció életciklusának kezeléséért felelős szolgáltatás
 */
@Service
public class SimulationLifecycleManager {

    private final RequestDataService requestDataService;
    private final IaaSManagerService iaaSManagerService;
    private final EnergyService energyService;
    private final VirtualMachineFactory vmFactory;
    private final SimulationStatsService simulationStatsService;

    private long starttime = 0;
    private long stoptime = 0;

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
     * Inicializálja a szimulációt adott számú fizikai géphez
     */
    public SimulationContext initializeSimulation(int physicalMachineCount) throws Exception {
        RequestData lastUpdateData = requestDataService.getLastData();
        IaaSContext iaasContext = iaaSManagerService.initializeIaaS(physicalMachineCount);
        SimulationContext context = new SimulationContext(iaasContext);

        vmFactory.createVirtualMachines(lastUpdateData, context);
        return context;
    }

    /**
     * Elindítja a szimulációs események futását és inicializálja az energiafogyasztási modellt
     */
    public void startSimulation(SimulationContext context) {
        Timed.simulateUntilLastEvent();
        setStarttime(Timed.getFireCount());
        energyService.setupEDC(context.getIaasContext());
    }

    /**
     * Lefuttatja a szimulációt adott percig
     */
    public void runSimulation(int minutes) {
        Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * minutes));
    }

    /**
     * Szimuláció lezárása, statisztikák generálása és erőforrások felszabadítása
     */
    public String finalizeSimulation(SimulationContext context, RequestData lastUpdateData,
                                     Map<String, VirtualMachine> backUpVms) {
        setStoptime(Timed.getFireCount());
        double totalEnergyConsumption = energyService.stopEDC();
        context.getMetrics().addEnergyConsumption(totalEnergyConsumption);

        String stats = simulationStatsService.generateRuntimeStats(
                getStoptime() - getStarttime(),
                lastUpdateData,
                context.getMetrics().getTotalEnergyConsumption(),
                context.getMetrics().getTotalMovedData(),
                context.getNumberOfVms(),
                backUpVms,
                context.getMetrics().getTotalTasks()
        );

        cleanupSimulation(backUpVms);
        return stats;
    }

    private void cleanupSimulation(Map<String, VirtualMachine> backUpVms) {
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
        Timed.resetTimed();

        if (backUpVms != null && !backUpVms.isEmpty()) {
            backUpVms.clear();
        }
    }

    // Getters and Setters
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