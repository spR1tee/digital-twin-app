package hu.digital_twin.service.infrastructure;

import hu.digital_twin.context.IaaSContext;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class IaaSManagerService {
    private final RepositoryService repositoryService;
    private final PhysicalMachineService physicalMachineService;
    private final PowerStateService powerStateService;

    public IaaSManagerService(RepositoryService repositoryService, PhysicalMachineService physicalMachineService, PowerStateService powerStateService) {
        this.repositoryService = repositoryService;
        this.physicalMachineService = physicalMachineService;
        this.powerStateService = powerStateService;
    }

    /**
     * IaaS környezet inicializálása
     */
    public IaaSContext initializeIaaS(int initialPMCount) throws Exception {
        // IaaS Service létrehozása
        IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);

        // Power transitions generálása
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                powerStateService.generateDefaultTransitions();

        // Cloud repository létrehozása
        Repository cloudRepo = repositoryService.createCloudRepository();
        iaas.registerRepository(cloudRepo);

        // IaaS Context létrehozása
        IaaSContext context = new IaaSContext(iaas, cloudRepo, transitions);

        // Fizikai gépek hozzáadása
        addPhysicalMachines(context, initialPMCount);

        return context;
    }

    /**
     * Fizikai gépek hozzáadása a kontextushoz
     */
    public void addPhysicalMachines(IaaSContext context, int count) throws NetworkNode.NetworkException {
        for (int i = 0; i < count; i++) {
            int index = context.pms.size() + 1;

            // Fizikai gép létrehozása és konfigurálása
            PhysicalMachine pm = physicalMachineService.createAndConfigurePhysicalMachine(
                    index, context.cloudRepo, context.pmRepos, context.transitions
            );

            // Repository hozzáadása a listához
            Repository pmRepo = pm.localDisk;
            context.pmRepos.add(pmRepo);

            // Fizikai gép regisztrálása
            context.iaas.registerHost(pm);
            context.pms.add(pm);
        }
    }
}
