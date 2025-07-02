package hu.digital_twin.service.simulation;

import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.util.DataSenderService;
import hu.digital_twin.service.prediction.PredictionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * A szimulációk kezeléséért felelős szolgáltatás.
 * Koordinálja a predikciók lekérését és az előrejelzett jövőbeli VM viselkedés szimulálását.
 */
@Service
public class SimulationHandlerService {

    private final DataSenderService dataSenderService;
    private final PredictionService predictionService;
    private final SimulationService simulationService;

    /**
     * Konstruktor dependency injection-nel.
     *
     * @param dataSenderService az adatok továbbításáért felelős szolgáltatás
     * @param predictionService a predikciós adatok előállításáért felelős szolgáltatás
     * @param simulationService a szimulációs logikát megvalósító szolgáltatás
     */
    public SimulationHandlerService(DataSenderService dataSenderService,
                                    PredictionService predictionService,
                                    SimulationService simulationService) {
        this.dataSenderService = dataSenderService;
        this.predictionService = predictionService;
        this.simulationService = simulationService;
    }

    /**
     * Elindítja a predikciós folyamatot – gépi tanulási modell segítségével becsli a jövőbeli terhelést.
     *
     * @param requestData a kérésben kapott adatok
     */
    public void handlePrediction(RequestData requestData) {
        Map<String, List<Double>> predictionData = predictionService.predict(requestData);
        // A predikció eredményét itt még nem használjuk, csak lekérjük
        // További feldolgozása máshol történik
    }

    /**
     * Elküldi a jövőbeli szimulált VM viselkedésre vonatkozó eredményeket három különböző szimuláció után:
     * - baseline (alap szimuláció, predikció nélkül),
     * - predikció alapú, skálázás nélküli szimuláció,
     * - predikció alapú, skálázást is figyelembe vevő szimuláció.
     *
     * @param requestData az input kérésadatok
     * @throws SimulationException ha bármelyik szimuláció hibába ütközik
     */
    public void sendFutureBehaviour(RequestData requestData) throws SimulationException {
        // 1. Alap szimuláció (baseline)
        dataSenderService.sendData(
                simulationService.doBaseline(requestData),
                "http://localhost:8082/dummy/receiveData"
        );

        // 2. Predikcióval, de skálázás nélkül
        dataSenderService.sendData(
                simulationService.usePredictionWithoutScaling(requestData),
                "http://localhost:8082/dummy/receiveData"
        );

        // 3. Predikcióval és dinamikus skálázással
        dataSenderService.sendData(
                simulationService.usePredictionWithScaling(requestData),
                "http://localhost:8082/dummy/receiveData"
        );
    }
}
