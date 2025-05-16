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
                simulation.prediction(requestData);
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


    public void sendData(String data, String url) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>(data, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        //System.out.println(response.getBody());
    }

}

