package hu.digital_twin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("simulation")
@Component
public class SimulationConfig {
    private int defaultPhysicalMachines = 1;
    private int scalingPhysicalMachines = 2;

    public int getDefaultPhysicalMachines() { return defaultPhysicalMachines; }
    public void setDefaultPhysicalMachines(int defaultPhysicalMachines) { this.defaultPhysicalMachines = defaultPhysicalMachines; }
    public int getScalingPhysicalMachines() { return scalingPhysicalMachines; }
    public void setScalingPhysicalMachines(int scalingPhysicalMachines) { this.scalingPhysicalMachines = scalingPhysicalMachines; }
}
