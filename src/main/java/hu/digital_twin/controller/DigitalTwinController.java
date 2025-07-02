package hu.digital_twin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.context.TenantContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.io.RequestHandlerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/simulator") // Az összes endpoint ezen az útvonalon belül lesz
public class DigitalTwinController {

    private final RequestHandlerService requestHandlerService;

    // Konstruktorban injektáljuk a RequestHandlerService-t
    public DigitalTwinController(RequestHandlerService requestHandlerService) {
        this.requestHandlerService = requestHandlerService;
    }

    // POST metódus a /simulator/request végponton
    @PostMapping("/request")
    public ResponseEntity<String> request(@RequestBody String jsonContent) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // A JSON tartalom deszerializálása RequestData objektummá
            RequestData requestData = objectMapper.readValue(jsonContent, RequestData.class);

            // A beérkezett adat kezelése a service rétegben
            requestHandlerService.handleRequest(requestData);

            // TenantContext törlése, hogy elkerüljük szálkezelési hibákat
            TenantContext.clear();

            return ResponseEntity.ok("Data has been processed.");
        } catch (IOException e) {
            e.printStackTrace();
            TenantContext.clear();
            return ResponseEntity.status(500).body("Error while processing data.");
        } catch (SimulationException e) {
            throw new RuntimeException(e);
        }
    }
}
