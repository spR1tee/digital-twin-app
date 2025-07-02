package hu.digital_twin.strategy;

import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PredictionWithScalingStrategy implements SimulationStrategy {
    private final SimulationService simulationService;

    public PredictionWithScalingStrategy(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @Override
    public String execute(RequestData requestData) throws SimulationException {
        try {
            Map<String, VirtualMachine> backUpVms = new ConcurrentHashMap<>();

            SimulationContext context = simulationService.initializeSimulation(
                    simulationService.getConfig().getScalingPhysicalMachines());
            RequestData lastUpdateData = simulationService.getRequestDataService().getLastData();

            ProcessedPredictionData predictionData = simulationService.processPerMinuteData(
                    simulationService.getPredictionService().predict(requestData), context);

            simulationService.startSimulation(context);

            int lastBackupCreationMinute = simulationService.executeScalingTasks(
                    context, predictionData, lastUpdateData, backUpVms, requestData.getThreshold());

            simulationService.runSimulation(requestData.getPredictionLength() - lastBackupCreationMinute);
            return simulationService.finalizeSimulation(context, lastUpdateData, backUpVms);

        } catch (Exception e) {
            throw new SimulationException("Prediction with scaling simulation failed", e);
        }
    }
}
