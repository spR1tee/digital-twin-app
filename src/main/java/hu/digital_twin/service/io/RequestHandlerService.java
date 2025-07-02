package hu.digital_twin.service.io;

import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationHandlerService;
import org.springframework.stereotype.Service;

/**
 * A beérkező szimulációs kérések feldolgozásáért felelős szolgáltatás.
 */
@Service
public class RequestHandlerService {

    private final RequestDataService requestDataService;
    private final SimulationHandlerService simulationHandlerService;

    public RequestHandlerService(RequestDataService requestDataService,
                                 SimulationHandlerService simulationHandlerService) {
        this.requestDataService = requestDataService;
        this.simulationHandlerService = simulationHandlerService;
    }

    /**
     * A beérkező kérés feldolgozása a típus alapján.
     *
     * @param requestData a kérés tartalma
     * @throws SimulationException ha a predikciós szimuláció során hiba történik
     */
    public void handleRequest(RequestData requestData) throws SimulationException {
        String requestType = requestData.getRequestType().toUpperCase();

        switch (requestType) {
            case "UPDATE":
                // Mentés az adatbázisba
                requestDataService.createRequestData(requestData);
                break;

            case "REQUEST PREDICTION":
                // Szimuláció futtatása predikcióval
                simulationHandlerService.handlePrediction(requestData);
                break;

            case "REQUEST FUTURE BEHAVIOUR":
                // Jövőbeli viselkedés lekérdezése (pl. energiafogyasztás, skálázás)
                simulationHandlerService.sendFutureBehaviour(requestData);
                break;

            default:
                throw new IllegalArgumentException("Unknown request type: " + requestType);
        }
    }
}
