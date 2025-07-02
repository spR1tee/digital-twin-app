package hu.digital_twin.strategy;

import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Szimulációs stratégia, amely predikciós adatok alapján
 * dinamikusan skálázza a virtuális gépeket (backup VM-ek létrehozása)
 * és ennek megfelelően osztja el a feladatokat.
 */
@Component
public class PredictionWithScalingStrategy implements SimulationStrategy {

    private final SimulationService simulationService;

    public PredictionWithScalingStrategy(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * A stratégia végrehajtása:
     * - Inicializál egy ConcurrentHashMap-et a backup VM-ek tárolására,
     *   hogy párhuzamos környezetben is biztonságos legyen az elérésük
     * - Inicializálja a szimulációt a konfigurációból származó skálázott fizikai gépek számával
     * - Lekéri az utolsó kérés adatait
     * - Meghívja a predikciós modellt, majd a kapott adatokat percenként feldolgozza
     * - Elindítja a szimulációt
     * - A predikciós adatok és konfiguráció alapján végrehajtja a skálázási feladatokat,
     *   amelyek tartalmazzák az esetleges backup VM-ek létrehozását és feladatok szétosztását
     * - Lefuttatja a szimulációt a fennmaradó időre, miután figyelembe vette az utolsó backup VM létrehozásának idejét
     * - Lezárja a szimulációt, visszaadja a statisztikákat, és átadja a backup VM-ek listáját a továbbfeldolgozáshoz
     *
     * @param requestData bemeneti kérés adatai, pl. VM szám, küszöbértékek, előrejelzés időtartama
     * @return a szimuláció eredményét tartalmazó string (pl. statisztikák)
     * @throws SimulationException ha a szimuláció során hiba történik
     */
    @Override
    public String execute(RequestData requestData) throws SimulationException {
        try {
            // Backup VM-ek biztonságos tárolása párhuzamos környezetben
            Map<String, VirtualMachine> backUpVms = new ConcurrentHashMap<>();

            // Szimuláció inicializálása a skálázáshoz ajánlott fizikai gépek számával
            SimulationContext context = simulationService.initializeSimulation(
                    simulationService.getConfig().getScalingPhysicalMachines());

            // Utolsó kérés adatainak lekérése a VM paraméterekhez
            RequestData lastUpdateData = simulationService.getRequestDataService().getLastData();

            // Predikció futtatása és adatok percenkénti feldolgozása a VM-ekhez
            ProcessedPredictionData predictionData = simulationService.processPerMinuteData(
                    simulationService.getPredictionService().predict(requestData), context);

            // Szimuláció elindítása (időzítés, energia mérés)
            simulationService.startSimulation(context);

            // Skálázási feladatok végrehajtása: backup VM-ek létrehozása, feladatok szétosztása
            int lastBackupCreationMinute = simulationService.executeScalingTasks(
                    context, predictionData, lastUpdateData, backUpVms, requestData.getThreshold());

            // Szimuláció futtatása a predikció időtartamából levonva a backup VM létrehozásáig eltelt időt
            simulationService.runSimulation(requestData.getPredictionLength() - lastBackupCreationMinute);

            // Szimuláció lezárása, eredmények és backup VM-ek átadása további feldolgozásra
            return simulationService.finalizeSimulation(context, lastUpdateData, backUpVms);

        } catch (Exception e) {
            throw new SimulationException("Prediction with scaling simulation failed", e);
        }
    }
}
