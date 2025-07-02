package hu.digital_twin.context;

import hu.digital_twin.service.simulation.SimulationMetrics;

import java.util.HashMap;
import java.util.Map;

public class SimulationContext {
    private final IaaSContext iaasContext;
    private final Map<String, Long> maxInstrPerSecond;
    private final Map<String, Integer> fileSizes;
    private final SimulationMetrics metrics;
    private int numberOfVms = 0;

    public SimulationContext(IaaSContext iaasContext) {
        this.iaasContext = iaasContext;
        this.maxInstrPerSecond = new HashMap<>();
        this.fileSizes = new HashMap<>();
        this.metrics = new SimulationMetrics();
    }

    public IaaSContext getIaasContext() { return iaasContext; }
    public Map<String, Long> getMaxInstrPerSecond() { return maxInstrPerSecond; }
    public Map<String, Integer> getFileSizes() { return fileSizes; }
    public SimulationMetrics getMetrics() { return metrics; }
    public int getNumberOfVms() { return numberOfVms; }
    public void incrementVmCount() { this.numberOfVms++; }
}
