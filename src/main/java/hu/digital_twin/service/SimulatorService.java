package hu.digital_twin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.digital_twin.Simulation;
import hu.digital_twin.model.RequestData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SimulatorService {

    @Autowired
    private RequestDataService requestDataService;
    @Autowired
    private Simulation simulation;

    public SimulatorService() {
    }

    public void handleRequest(RequestData requestData) {
        String request_type = requestData.getRequestType().toUpperCase();
        switch (request_type) {
            case "UPDATE":
                requestDataService.createRequestData(requestData);
                /*List<RequestData> query = requestDataService.getAllRequestData();
                for(RequestData rd : query) {
                    System.out.println(rd.toString());
                }*/
                break;
            case "REQUEST PREDICTION":
                prediction(requestData);
                break;
            case "REQUEST FUTURE BEHAVIOUR":
                simulation.do_baseline(requestData);
                simulation.do_alternative("down", requestData);
                simulation.do_alternative("up", requestData);
                break;
            default:
                System.err.println("Error: Unknown Request Type");
                break;
        }
    }

    public Map<String, List<Double>> prediction(RequestData requestData) {
        try {
            String scriptPath = "src/main/resources/scripts/linear_regression_model.py";
            ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, requestData.getFeatureName(), Integer.toString(requestData.getBasedOnLast()), Integer.toString(requestData.getPredictionLength()), Integer.toString(requestData.getVmsCount()));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder jsonData = new StringBuilder();
            boolean jsonStarted = false;
            boolean jsonEnded = false;
            Map<String, List<Double>> predictionData = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                if (line.equals("JSON_DATA_START")) {
                    jsonStarted = true;
                    continue;
                }
                if (line.equals("JSON_DATA_END")) {
                    jsonEnded = true;
                    continue;
                }

                if (jsonStarted && !jsonEnded) {
                    jsonData.append(line);
                } else {
                    System.out.println(line);
                }
            }

            if (!jsonData.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                predictionData = mapper.readValue(
                        jsonData.toString(),
                        new TypeReference<Map<String, List<Double>>>() {}
                );
            }
            //sendData(line, "http://localhost:8082/dummy/receiveData");
            int exitCode = process.waitFor();
            System.out.println("Python script exited with code: " + exitCode);
            return predictionData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void sendData(String data, String url) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>(data, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        //System.out.println(response.getBody());
    }


}

