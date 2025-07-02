package hu.digital_twin.service.io;

import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationHandlerService;
import org.springframework.stereotype.Service;

@Service
public class RequestHandlerService {

    private final RequestDataService requestDataService;
    private final SimulationHandlerService simulationHandlerService;

    public RequestHandlerService(RequestDataService requestDataService,
                                 SimulationHandlerService simulationHandlerService) {
        this.requestDataService = requestDataService;
        this.simulationHandlerService = simulationHandlerService;
    }

    public void handleRequest(RequestData requestData) throws SimulationException {
        String requestType = requestData.getRequestType().toUpperCase();
        switch (requestType) {
            case "UPDATE":
                requestDataService.createRequestData(requestData);
                break;
            case "REQUEST PREDICTION":
                simulationHandlerService.handlePrediction(requestData);
                break;
            case "REQUEST FUTURE BEHAVIOUR":
                simulationHandlerService.sendFutureBehaviour(requestData);
                break;
            default:
                throw new IllegalArgumentException("Unknown request type: " + requestType);
        }
    }
}
