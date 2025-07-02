package hu.digital_twin.service.prediction;

import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.service.simulation.SimulationConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Predikciós adatok feldolgozásáért felelős szolgáltatás
 */
@Service
public class PredictionDataProcessor {

    /**
     * Előkészíti a predikciós adatokat percenkénti feldolgozásra
     * Minden VM-hez kiszámolja:
     *   - az átlagos terhelést percenként
     *   - a generált utasításszámot percenként
     */
    public ProcessedPredictionData processPerMinuteData(Map<String, List<Double>> predictionData,
                                                        SimulationContext context) {
        Map<String, List<Double>> avgLoadsPerMinute = new HashMap<>();
        Map<String, List<Integer>> taskInstructionsPerMinute = new HashMap<>();

        // Minden VM terhelési sorozatát feldolgozza párhuzamosan
        predictionData.entrySet().parallelStream().forEach(entry -> {
            String vmId = entry.getKey();
            List<Double> loadValues = entry.getValue();

            // Egy VM adatainak feldolgozása
            ProcessedMinuteData minuteData = processVmLoadData(vmId, loadValues, context);

            // Eredmények tárolása szinkronizált módon
            synchronized (avgLoadsPerMinute) {
                avgLoadsPerMinute.put(vmId, minuteData.getAvgLoads());
                taskInstructionsPerMinute.put(vmId, minuteData.getInstructions());
            }
        });

        // Feldolgozott adatok visszaadása
        return new ProcessedPredictionData(avgLoadsPerMinute, taskInstructionsPerMinute);
    }

    /**
     * Egy VM predikciós sorozatának feldolgozása:
     * percenként átlagolás + utasításszám kiszámítás
     */
    private ProcessedMinuteData processVmLoadData(String vmId, List<Double> loadValues, SimulationContext context) {
        List<Double> avgLoads = new ArrayList<>();
        List<Integer> instructions = new ArrayList<>();

        // A teljes lista feldarabolása percenkénti ablakokra
        for (int i = 0; i < loadValues.size(); i += SimulationConstants.READINGS_PER_MINUTE) {
            int endIdx = Math.min(i + SimulationConstants.READINGS_PER_MINUTE, loadValues.size());
            List<Double> window = loadValues.subList(i, endIdx);

            // Átlagterhelés és utasításszám kiszámítása
            double avg = calculateAverageLoad(window);
            int instr = calculateInstructionsForMinute(avg, vmId, context);

            avgLoads.add(avg);
            instructions.add(instr);
        }

        return new ProcessedMinuteData(avgLoads, instructions);
    }

    /**
     * Egy adott időablak (perc) terhelési értékeinek átlaga
     */
    private double calculateAverageLoad(List<Double> window) {
        return window.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    /**
     * Egy perc alatt elvégzendő utasításmennyiség kiszámítása a terhelés alapján
     */
    private int calculateInstructionsForMinute(double avgLoad, String vmId, SimulationContext context) {
        return (int) Math.round(SimulationConstants.SECONDS_PER_MINUTE *
                (avgLoad / 100.0) * context.getMaxInstrPerSecond().get(vmId));
    }

    /**
     * VM percenkénti feldolgozott adatokat tároló belső osztály
     */
    private static class ProcessedMinuteData {
        private final List<Double> avgLoads;
        private final List<Integer> instructions;

        public ProcessedMinuteData(List<Double> avgLoads, List<Integer> instructions) {
            this.avgLoads = avgLoads;
            this.instructions = instructions;
        }

        public List<Double> getAvgLoads() {
            return avgLoads;
        }

        public List<Integer> getInstructions() {
            return instructions;
        }
    }
}
