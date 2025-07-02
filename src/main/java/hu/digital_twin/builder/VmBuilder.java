package hu.digital_twin.builder;

import hu.digital_twin.context.SimulationContext;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class VmBuilder {
    // Virtuális gép attribútumai
    private String name;
    private long startupProcess;
    private long networkTraffic;
    private long reqDisk;
    private double cpu;
    private double coreProcessingPower;
    private long ram;

    // VM név beállítása (pl. azonosító)
    public VmBuilder withName(String name) {
        this.name = name;
        return this;
    }

    // Indulási folyamatok száma vagy időtartama
    public VmBuilder withStartupProcess(long startupProcess) {
        this.startupProcess = startupProcess;
        return this;
    }

    // Hálózati forgalom paraméter (pl. sávszélesség)
    public VmBuilder withNetworkTraffic(long networkTraffic) {
        this.networkTraffic = networkTraffic;
        return this;
    }

    // A VM-hez rendelt lemezterület mérete (byte)
    public VmBuilder withDisk(long reqDisk) {
        this.reqDisk = reqDisk;
        return this;
    }

    // CPU, core processing power és RAM konfiguráció beállítása
    public VmBuilder withResources(double cpu, double coreProcessingPower, long ram) {
        this.cpu = cpu;
        this.coreProcessingPower = coreProcessingPower;
        this.ram = ram;
        return this;
    }

    // Virtuális gép létrehozása a szimulációs kontextusban a beállított paraméterekkel
    public VirtualMachine build(SimulationContext context) throws Exception {
        // VirtualAppliance példány létrehozása, ami a VM egy logikai egysége
        VirtualAppliance va = new VirtualAppliance(name, startupProcess, networkTraffic, false, reqDisk);

        // Erőforrás korlátok definiálása (CPU, teljesítmény, memória)
        AlterableResourceConstraints arc = new AlterableResourceConstraints(cpu, coreProcessingPower, ram);

        // VirtualAppliance regisztrálása az első IaaS repository-ban
        context.getIaasContext().iaas.repositories.get(0).registerObject(va);

        // VM kérés a szimulációs infrastruktúrán: 1 példány kérés
        VirtualMachine[] vms = context.getIaasContext().iaas.requestVM(va, arc,
                context.getIaasContext().iaas.repositories.get(0), 1);

        // Az első (és jelen esetben egyetlen) virtuális gép visszaadása
        return vms[0];
    }
}
