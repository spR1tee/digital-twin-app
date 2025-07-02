package hu.digital_twin.service.infrastructure;

import hu.digital_twin.context.IaaSContext;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import org.springframework.stereotype.Service;

@Service
public class EnergyService {

    public void setupEDC(IaaSContext context) {
        int index = 1;
        for (PhysicalMachine pm : context.iaas.machines) {
            String name = "pm-" + index++;
            new EnergyDataCollector(name, pm, true);
        }
    }

    public double stopEDC() {
        double totalEnergy = 0.0;
        for (EnergyDataCollector edc : EnergyDataCollector.energyCollectors) {
            totalEnergy += edc.energyConsumption / 1000 / 3_600_000;
            edc.stop();
        }
        EnergyDataCollector.energyCollectors.clear();
        return totalEnergy;
    }
}

