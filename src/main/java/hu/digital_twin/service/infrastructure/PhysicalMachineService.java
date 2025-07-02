package hu.digital_twin.service.infrastructure;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PhysicalMachineService {
    private final RepositoryService repositoryService;
    private final NetworkConfigurationService networkService;

    public PhysicalMachineService(RepositoryService repositoryService, NetworkConfigurationService networkService) {
        this.repositoryService = repositoryService;
        this.networkService = networkService;
    }

    /**
     * Fizikai gép létrehozása
     */
    public PhysicalMachine createPhysicalMachine(Repository repository,
                                                 EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions) {
        return new PhysicalMachine(
                8, 1, 17_179_869_184L,
                repository, 0, 0,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host)
        );
    }

    /**
     * Fizikai gép teljes létrehozása és konfigurációja
     */
    public PhysicalMachine createAndConfigurePhysicalMachine(int index, Repository cloudRepo,
                                                             List<Repository> existingRepos,
                                                             EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions) throws NetworkNode.NetworkException {

        // Repository létrehozása
        Repository pmRepo = repositoryService.createPhysicalMachineRepository(index);

        // Hálózati integráció
        networkService.integrateRepositoryToNetwork(pmRepo, cloudRepo, existingRepos);

        // Fizikai gép létrehozása

        return createPhysicalMachine(pmRepo, transitions);
    }
}
