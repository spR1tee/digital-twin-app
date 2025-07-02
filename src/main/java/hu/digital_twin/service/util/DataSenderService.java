package hu.digital_twin.service.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Szolgáltatás HTTP POST kérések JSON adatok küldésére.
 * Egyszerű wrapper a Spring RestTemplate felett.
 */
@Service
public class DataSenderService {

    // RestTemplate példány, amely HTTP kérések lebonyolításáért felel
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * JSON formátumú adat elküldése egy megadott URL-re HTTP POST metódussal.
     *
     * @param data a JSON formátumú string, amit küldeni kell
     * @param url a cél URL, ahová az adatot küldjük
     */
    public void sendData(String data, String url) {
        // HTTP fejlécek beállítása, itt Content-Type: application/json
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // HttpEntity objektum létrehozása a kéréshez: tartalom + fejlécek
        HttpEntity<String> request = new HttpEntity<>(data, headers);

        restTemplate.postForEntity(url, request, String.class);
    }
}
