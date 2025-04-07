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

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class SimulatorService {

    @Autowired
    private RequestDataService requestDataService;
    @Autowired
    private Simulation simulation;

    public SimulatorService() {}

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
                try {
                    String scriptPath = "src/main/resources/scripts/linear_regression_model.py";
                    ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, requestData.getFeatureName(), Integer.toString(requestData.getBasedOnLast()), Integer.toString(requestData.getPredictionLength()));
                    processBuilder.redirectErrorStream(true);
                    Process process = processBuilder.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        sendData(line, "http://localhost:8082/dummy/receiveData");
                    }

                    int exitCode = process.waitFor();
                    System.out.println("Python script exited with code: " + exitCode);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
            case "REQUEST FUTURE BEHAVIOUR":
                simulation.do_baseline();
                simulation.do_alternative("down");
                simulation.do_alternative("up");
                break;
            default:
                System.err.println("Error: Unknown Request Type");
                break;
        }
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

