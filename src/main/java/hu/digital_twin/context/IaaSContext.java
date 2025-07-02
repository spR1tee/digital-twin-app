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
    // IaaS szolgáltatás, amely kezeli a felhő infrastruktúrát (pl. VM-ek, fizikai gépek)
    public IaaSService iaas;

    // Fizikai gépek listája az IaaS környezetben
    public List<PhysicalMachine> pms = new ArrayList<>();

    // Fizikai gépekhez tartozó adattárak (repository-k)
    public List<Repository> pmRepos = new ArrayList<>();

    // Központi felhő adattár, ahol például VM-ek és egyéb objektumok tárolódnak
    public Repository cloudRepo;

    // Energia állapotátmenetek tárolása, ami a gépek teljesítményállapotait írja le
    // Minden PowerStateKind (pl. bekapcsolás, alvó mód, kikapcsolás) egy mapen belül van VM nevekkel és azok állapotaival
    public final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions;

    // Konstruktor a szükséges IaaS komponensek inicializálásához
    public IaaSContext(IaaSService iaas,
                       Repository cloudRepo,
                       EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions) {
        this.iaas = iaas;
        this.cloudRepo = cloudRepo;
        this.transitions = transitions;
    }
}
