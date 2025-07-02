package hu.digital_twin.strategy;

import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Component;

/**
 * Baseline szimulációs stratégia, amely konstans (nem skálázott) terhelést alkalmaz a VM-ekre.
 */
@Component
public class BaselineSimulationStrategy implements SimulationStrategy {

    private final SimulationService simulationService;

    public BaselineSimulationStrategy(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * A stratégia végrehajtása:
     * - Inicializálja a szimulációt az alapértelmezett fizikai gépek számával
     * - Lekéri az utolsó mentett kérés adatait
     * - Elindítja a szimulációt
     * - Minden fizikai gépen és azon belül minden virtuális gépen konstans terhelést hajt végre (baseline feladatokat)
     * - Lefuttatja a szimulációt a kért időtartamig
     * - Lezárja a szimulációt és visszaadja az eredményeket
     *
     * @param requestData a bemeneti kérés adatai
     * @return a szimuláció eredményét tartalmazó string (pl. statisztikák)
     * @throws SimulationException ha a szimuláció során hiba történik
     */
    @Override
    public String execute(RequestData requestData) throws SimulationException {
        try {
            // Szimuláció inicializálása az alapértelmezett fizikai gépek számával
            SimulationContext context = simulationService.initializeSimulation(
                    simulationService.getConfig().getDefaultPhysicalMachines());

            // Az utolsó kérés adatainak lekérése (pl. VM paraméterek)
            RequestData lastUpdateData = simulationService.getRequestDataService().getLastData();

            // Szimuláció elindítása (időzítő és energia mérés beállítása)
            simulationService.startSimulation(context);

            // Minden fizikai gép és azon belüli VM-ek feldolgozása
            for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
                for (VirtualMachine vm : pm.listVMs()) {
                    // VM számláló növelése a kontextusban
                    context.incrementVmCount();

                    // Baseline feladatok végrehajtása: konstans terhelés VM-enként
                    simulationService.executeBaselineTasks(vm, lastUpdateData, context, requestData.getPredictionLength());
                }
            }

            // Szimuláció futtatása a kérésben megadott ideig (percben)
            simulationService.runSimulation(requestData.getPredictionLength());

            // Szimuláció lezárása, erőforrások felszabadítása, statisztikák készítése és visszaadása
            return simulationService.finalizeSimulation(context, lastUpdateData, null);

        } catch (Exception e) {
            // Hibakezelés: SimulationException dobása, ha bármilyen hiba történik
            throw new SimulationException("Baseline simulation failed", e);
        }
    }
}
