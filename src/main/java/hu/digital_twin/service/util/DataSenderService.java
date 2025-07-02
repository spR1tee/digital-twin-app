package hu.digital_twin.service.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DataSenderService {

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendData(String data, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(data, headers);
        restTemplate.postForEntity(url, request, String.class);
    }
}