package hu.digital_twin.strategy;

import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Component;

@Component
public class PredictionWithoutScalingStrategy implements SimulationStrategy {
    private final SimulationService simulationService;

    public PredictionWithoutScalingStrategy(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @Override
    public String execute(RequestData requestData) throws SimulationException {
        try {
            SimulationContext context = simulationService.initializeSimulation(
                    simulationService.getConfig().getDefaultPhysicalMachines());
            RequestData lastUpdateData = simulationService.getRequestDataService().getLastData();

            ProcessedPredictionData predictionData = simulationService.processPerMinuteData(
                    simulationService.getPredictionService().predict(requestData), context);

            simulationService.startSimulation(context);

            // VM-ek feldolgozása predikciós adatokkal
            for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
                for (VirtualMachine vm : pm.listVMs()) {
                    context.incrementVmCount();
                    simulationService.executeNonScalingTasks(vm, predictionData, context);
                }
            }

            simulationService.runSimulation(requestData.getPredictionLength());
            return simulationService.finalizeSimulation(context, lastUpdateData, null);

        } catch (Exception e) {
            throw new SimulationException("Prediction without scaling simulation failed", e);
        }
    }
}
