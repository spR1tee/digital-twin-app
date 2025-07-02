package hu.digital_twin.service.infrastructure;

import hu.digital_twin.context.IaaSContext;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import org.springframework.stereotype.Service;

@Service
public class EnergyService {

    /**
     * Beállítja és elindítja az energiafogyasztás gyűjtését minden fizikai géphez az IaaS kontextusban.
     * Minden fizikai géphez létrehoz egy EnergyDataCollector példányt, ami kezeli az energiafigyelést.
     *
     * @param context Az IaaS környezet és a fizikai gépek kontextusa.
     */
    public void setupEDC(IaaSContext context) {
        int index = 1;
        for (PhysicalMachine pm : context.iaas.machines) {
            String name = "pm-" + index++; // Egyedi név a PM-eknek (pl. pm-1, pm-2...)
            new EnergyDataCollector(name, pm, true); // Új energiafigyelő indítása a géphez
        }
    }

    /**
     * Leállítja az összes energiafigyelőt és összesíti az energiafogyasztást.
     * Az energiaértéket kilowattórában (kWh) adja vissza.
     *
     * @return A teljes energiafogyasztás kWh-ban.
     */
    public double stopEDC() {
        double totalEnergy = 0.0;

        // Végigmegy az összes energiafigyelőn
        for (EnergyDataCollector edc : EnergyDataCollector.energyCollectors) {
            // Energiafogyasztás átszámítása kWh-ba:
            totalEnergy += edc.energyConsumption / 1000 / 3_600_000;

            edc.stop(); // Energiafigyelő leállítása
        }

        EnergyDataCollector.energyCollectors.clear(); // Lista ürítése a következő futtatáshoz

        return totalEnergy;
    }
}
