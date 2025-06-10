package hu.digital_twin.service;

import hu.digital_twin.Simulation;
import hu.digital_twin.model.RequestData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class SimulatorService {
    // Szolgáltatás az adatok adatbázisba mentéséhez
    @Autowired
    private RequestDataService requestDataService;
    // A szimulációs logikát megvalósító komponens
    @Autowired
    private Simulation simulation;

    public SimulatorService() {
    }

    /**
     * A beérkezett kérés típusának megfelelően végrehajtja a megfelelő műveletet
     */
    public void handleRequest(RequestData requestData) {
        String request_type = requestData.getRequestType().toUpperCase();
        switch (request_type) {
            case "UPDATE":
                // UPDATE esetén mentjük az adatokat adatbázisba
                requestDataService.createRequestData(requestData);
                break;
            case "REQUEST PREDICTION":
                // Predikciós modell futtatása a kapott adatokra
                Map<String, List<Double>> predictionData = simulation.prediction(requestData);
                break;
            case "REQUEST FUTURE BEHAVIOUR":
                // A baseline és az előrejelzett adatok elküldése a kliensnek
                sendData(simulation.doBaseline(requestData), "http://localhost:8082/dummy/receiveData");
                sendData(simulation.usePrediction(requestData), "http://localhost:8082/dummy/receiveData");
                break;
            default:
                System.err.println("Error: Unknown Request Type");
                break;
        }
    }

    /**
     * JSON formátumú adat küldése POST kéréssel a megadott URL-re
     */
    public void sendData(String data, String url) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(data, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
    }

}

