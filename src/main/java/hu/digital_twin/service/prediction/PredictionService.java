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

@Service
public class PredictionService {

    /**
     * Python alapú gépi tanulási modell meghívása terhelés-előrejelzéshez.
     * Lineáris regressziós modellt használ a VM terhelések predikciójára.
     *
     * @param requestData Predikciós paraméterek
     * @return VM-enkénti terhelési előrejelzések
     */
    public Map<String, List<Double>> predict(RequestData requestData) {
        try {
            String scriptPath = "src/main/resources/scripts/prediction_new.py";
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python", scriptPath,
                    requestData.getFeatureName(),
                    Integer.toString(requestData.getBasedOnLast() * 12),
                    Integer.toString(requestData.getPredictionLength() * 60),
                    Integer.toString(requestData.getVmsCount()),
                    TenantContext.getTenantId(),
                    requestData.getModelType()
            );
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder jsonData = new StringBuilder();
            boolean jsonStarted = false;
            boolean jsonEnded = false;
            String line;

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

            if (!jsonData.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                return objectMapper.readValue(jsonData.toString(),
                        new TypeReference<Map<String, List<Double>>>() {});
            }

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyMap();
    }
}

