package hu.digital_twin.strategy;

import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.simulation.SimulationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Component;

@Component
public class BaselineSimulationStrategy implements SimulationStrategy {
    private final SimulationService simulationService;

    public BaselineSimulationStrategy(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @Override
    public String execute(RequestData requestData) throws SimulationException {
        try {
            SimulationContext context = simulationService.initializeSimulation(
                    simulationService.getConfig().getDefaultPhysicalMachines());
            RequestData lastUpdateData = simulationService.getRequestDataService().getLastData();

            simulationService.startSimulation(context);

            // Konstans terhelés alkalmazása
            for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
                for (VirtualMachine vm : pm.listVMs()) {
                    context.incrementVmCount();
                    simulationService.executeBaselineTasks(vm, lastUpdateData, context, requestData.getPredictionLength());
                }
            }

            simulationService.runSimulation(requestData.getPredictionLength());
            return simulationService.finalizeSimulation(context, lastUpdateData, null);

        } catch (Exception e) {
            throw new SimulationException("Baseline simulation failed", e);
        }
    }
}
