package hu.digital_twin.service.infrastructure;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class PowerStateService {

    /**
     * Alapértelmezett power state konfigurációk generálása
     */
    public EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> generateDefaultTransitions() {
        return PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
    }

    /**
     * Egyedi power state konfigurációk generálása
     */
    public EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> generateCustomTransitions(
            int hostIdleConsumption, int hostMaxConsumption, int hostTransitionTime,
            int storageIdleConsumption, int storageMaxConsumption) {
        return PowerTransitionGenerator.generateTransitions(
                hostIdleConsumption, hostMaxConsumption, hostTransitionTime,
                storageIdleConsumption, storageMaxConsumption
        );
    }
}