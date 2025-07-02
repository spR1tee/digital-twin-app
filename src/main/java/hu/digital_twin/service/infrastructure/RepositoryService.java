package hu.digital_twin.service.infrastructure;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Service
public class RepositoryService {
    private final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions;

    public RepositoryService(PowerStateService powerStateService) {

        this.transitions = powerStateService.generateDefaultTransitions();
    }

    /**
     * Létrehoz egy új repository-t a megadott paraméterekkel
     */
    public Repository createRepository(String name, long capacity) throws NetworkNode.NetworkException {
        Repository repo = new Repository(
                capacity, name,
                12_500, 12_500, 12_500,
                new HashMap<>(),
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)
        );
        repo.setState(NetworkNode.State.RUNNING);
        return repo;
    }
    /**
     * Központi cloud repository létrehozása
     */
    public Repository createCloudRepository() throws NetworkNode.NetworkException {
        return createRepository("cloudRepo", 107_374_182_400L);
    }

    /**
     * Fizikai gép repository létrehozása
     */
    public Repository createPhysicalMachineRepository(int index) throws NetworkNode.NetworkException {
        return createRepository("pmRepo" + index, 107_374_182_400L);
    }
}