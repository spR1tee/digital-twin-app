package hu.digital_twin.service.prediction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hu.digital_twin.context.TenantContext;
import hu.digital_twin.model.RequestData;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Predikciós szolgáltatás, amely gépi tanulási modellt hív meg Python szkripttel.
 * Jelenleg lineáris regressziós modellre épülő CPU-terhelés előrejelzésre használják.
 */
@Service
public class PredictionService {

    /**
     * Python alapú gépi tanulási modell meghívása terhelés-előrejelzéshez.
     * A paraméterek alapján elindítja a szkriptet, majd JSON formátumban várja az előrejelzéseket.
     *
     * @param requestData Predikciós paraméterek
     * @return VM-enkénti terhelési előrejelzések (map VM név és a hozzátartozó terhelési értékek listája)
     */
    public Map<String, List<Double>> predict(RequestData requestData) {
        try {
            // A python szkript elérési útja
            String scriptPath = "src/main/resources/scripts/prediction_new.py";

            // ProcessBuilder konfigurálása a python script paraméterezett futtatására
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python", scriptPath,
                    requestData.getFeatureName(),                                // prediktálandó jellemző (pl. CPU)
                    Integer.toString(requestData.getBasedOnLast() * 12),        // bemeneti minta hossz
                    Integer.toString(requestData.getPredictionLength() * 60),   // előrejelzés hossza másodpercben
                    Integer.toString(requestData.getVmsCount()),                // érintett VM-ek száma
                    TenantContext.getTenantId(),                                // aktuális tenant ID
                    requestData.getModelType()                                  // használt modell típusa (pl. linear_regression, arima, stb.)
            );

            processBuilder.redirectErrorStream(true);

            // Szkript futtatása
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder jsonData = new StringBuilder();
            boolean jsonStarted = false;
            boolean jsonEnded = false;
            String line;

            // A python kimenet JSON adatát olvassa be két jelölő között: JSON_DATA_START / END
            while ((line = reader.readLine()) != null) {
                //System.out.println(line);
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
                }
            }

            // JSON adat feldolgozása, ha van ilyen
            if (!jsonData.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                return objectMapper.readValue(jsonData.toString(),
                        new TypeReference<Map<String, List<Double>>>() {}); // A JSON egy Map<String, List<Double>> formátumú
            }

            // Megvárja a szkript teljes befejezését
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Hiba esetén üres map visszaadása
        return Collections.emptyMap();
    }
}
