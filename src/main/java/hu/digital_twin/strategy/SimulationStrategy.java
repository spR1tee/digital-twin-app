package hu.digital_twin.strategy;

import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.model.RequestData;

public interface SimulationStrategy {
    String execute(RequestData requestData) throws SimulationException;
}
