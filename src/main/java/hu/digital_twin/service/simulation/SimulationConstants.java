package hu.digital_twin.service.simulation;

public final class SimulationConstants {
    public static final int SECONDS_PER_READING = 5;
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int READINGS_PER_MINUTE = SECONDS_PER_MINUTE / SECONDS_PER_READING;
    public static final double LOAD_SPLIT_RATIO = 0.5;
    public static final double MIN_LOAD = 0.0;
    public static final double MAX_LOAD = 1.0;

    private SimulationConstants() {
        // Utility class
    }
}
