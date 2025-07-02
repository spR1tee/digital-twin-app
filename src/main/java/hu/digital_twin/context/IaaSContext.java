package hu.digital_twin.context;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class IaaSContext {
    public IaaSService iaas;
    public List<PhysicalMachine> pms = new ArrayList<>();
    public List<Repository> pmRepos = new ArrayList<>();
    public Repository cloudRepo;
    public final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions;

    public IaaSContext(IaaSService iaas,
                       Repository cloudRepo,
                       EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions) {
        this.iaas = iaas;
        this.cloudRepo = cloudRepo;
        this.transitions = transitions;
    }
}
