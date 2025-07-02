package hu.digital_twin.model;

import java.util.List;
import java.util.Map;

// Ez az osztály a predikció feldolgozott eredményeit tartalmazza,
// amelyek VM-enként percre bontva átlagos terhelést és feladatutasításokat tárolnak.
public class ProcessedPredictionData {

    // VM azonosítóhoz tartozó lista, amely percenkénti átlagos terhelési értékeket tartalmaz (0-100%)
    private final Map<String, List<Double>> avgLoadsPerMinute;

    // VM azonosítóhoz tartozó lista, amely percenkénti utasításszámokat tartalmaz
    private final Map<String, List<Integer>> taskInstructionsPerMinute;

    // Konstruktor
    public ProcessedPredictionData(Map<String, List<Double>> avgLoadsPerMinute,
                                   Map<String, List<Integer>> taskInstructionsPerMinute) {
        this.avgLoadsPerMinute = avgLoadsPerMinute;
        this.taskInstructionsPerMinute = taskInstructionsPerMinute;
    }

    // Getter metódusok az adatok eléréséhez

    public Map<String, List<Double>> getAvgLoadsPerMinute() {
        return avgLoadsPerMinute;
    }

    public Map<String, List<Integer>> getTaskInstructionsPerMinute() {
        return taskInstructionsPerMinute;
    }
}
