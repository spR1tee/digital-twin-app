package hu.digital_twin.exception;

public class SimulationException extends Exception {
    public SimulationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SimulationException(String message) {
        super(message);
    }
}
