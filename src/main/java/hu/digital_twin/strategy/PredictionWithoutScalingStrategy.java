package hu.digital_twin.strategy;

import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Component;

/**
 * Szimulációs stratégia, amely predikciós adatok alapján hajt végre
 * feladatokat, de nem skálázza dinamikusan a virtuális gépeket.
 */
@Component
public class PredictionWithoutScalingStrategy implements SimulationStrategy {

    private final SimulationService simulationService;

    public PredictionWithoutScalingStrategy(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Stratégia végrehajtása:
     * - Inicializálja a szimulációt az alapértelmezett fizikai gépek számával
     * - Lekéri az utolsó kérés adatait
     * - Meghívja a predikciós szolgáltatást és feldolgozza a percenkénti adatokat
     * - Elindítja a szimulációt
     * - VM-ekre alkalmazza a predikció alapú, de nem skálázott feladatokat
     * - Lefuttatja a szimulációt a megadott időtartamig
     * - Lezárja a szimulációt, generálja az eredményeket
     *
     * @param requestData a bemeneti kérés adatai
     * @return a szimuláció eredménye (statisztikák)
     * @throws SimulationException, ha hiba történik a szimuláció során
     */
    @Override
    public String execute(RequestData requestData) throws SimulationException {
        try {
            // Szimuláció inicializálása az alapértelmezett fizikai gépek számával
            SimulationContext context = simulationService.initializeSimulation(
                    simulationService.getConfig().getDefaultPhysicalMachines());

            // Utolsó kérés adatainak lekérése (VM paraméterek, konfiguráció)
            RequestData lastUpdateData = simulationService.getRequestDataService().getLastData();

            // Predikció lefuttatása Python modell segítségével, majd adatfeldolgozás percenként
            ProcessedPredictionData predictionData = simulationService.processPerMinuteData(
                    simulationService.getPredictionService().predict(requestData), context);

            // Szimuláció elindítása (időzítés, energia mérés beállítása)
            simulationService.startSimulation(context);

            // Fizikai gépek és VM-ek végigiterálása, nem skálázott predikciós feladatok végrehajtása
            for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
                for (VirtualMachine vm : pm.listVMs()) {
                    // VM számláló növelése a szimulációs kontextusban
                    context.incrementVmCount();

                    // Feladat végrehajtása VM-en a predikciós adatok alapján, de skálázás nélkül
                    simulationService.executeNonScalingTasks(vm, predictionData, context);
                }
            }

            // Szimuláció futtatása a megadott predikciós időhosszra (percben)
            simulationService.runSimulation(requestData.getPredictionLength());

            // Szimuláció lezárása, eredmények összeállítása és visszaadása
            return simulationService.finalizeSimulation(context, lastUpdateData, null);

        } catch (Exception e) {
            // Hibakezelés, egyedi SimulationException dobása
            throw new SimulationException("Prediction without scaling simulation failed", e);
        }
    }
}
