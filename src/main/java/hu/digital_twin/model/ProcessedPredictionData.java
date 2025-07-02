package hu.digital_twin.model;

import java.util.List;
import java.util.Map;

public class ProcessedPredictionData {
    private final Map<String, List<Double>> avgLoadsPerMinute;
    private final Map<String, List<Integer>> taskInstructionsPerMinute;

    public ProcessedPredictionData(Map<String, List<Double>> avgLoadsPerMinute,
                                   Map<String, List<Integer>> taskInstructionsPerMinute) {
        this.avgLoadsPerMinute = avgLoadsPerMinute;
        this.taskInstructionsPerMinute = taskInstructionsPerMinute;
    }

    public Map<String, List<Double>> getAvgLoadsPerMinute() { return avgLoadsPerMinute; }
    public Map<String, List<Integer>> getTaskInstructionsPerMinute() { return taskInstructionsPerMinute; }
}
