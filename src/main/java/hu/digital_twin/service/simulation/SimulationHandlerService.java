package hu.digital_twin.service.simulation;

import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.util.DataSenderService;
import hu.digital_twin.service.prediction.PredictionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SimulationHandlerService {

    private final DataSenderService dataSenderService;
    private final PredictionService predictionService;
    private final SimulationService simulationService;

    public SimulationHandlerService(DataSenderService dataSenderService, PredictionService predictionService, SimulationService simulationService) {
        this.dataSenderService = dataSenderService;
        this.predictionService = predictionService;
        this.simulationService = simulationService;
    }

    public void handlePrediction(RequestData requestData) {
        Map<String, List<Double>> predictionData = predictionService.predict(requestData);
    }

    public void sendFutureBehaviour(RequestData requestData) throws SimulationException {
        dataSenderService.sendData(simulationService.doBaseline(requestData), "http://localhost:8082/dummy/receiveData");
        dataSenderService.sendData(simulationService.usePredictionWithoutScaling(requestData), "http://localhost:8082/dummy/receiveData");
        dataSenderService.sendData(simulationService.usePredictionWithScaling(requestData), "http://localhost:8082/dummy/receiveData");
    }
}

