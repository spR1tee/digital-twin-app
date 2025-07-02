package hu.digital_twin.builder;

import hu.digital_twin.context.SimulationContext;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class VmBuilder {
    private String name;
    private long startupProcess;
    private long networkTraffic;
    private long reqDisk;
    private double cpu;
    private double coreProcessingPower;
    private long ram;

    public VmBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public VmBuilder withStartupProcess(long startupProcess) {
        this.startupProcess = startupProcess;
        return this;
    }

    public VmBuilder withNetworkTraffic(long networkTraffic) {
        this.networkTraffic = networkTraffic;
        return this;
    }

    public VmBuilder withDisk(long reqDisk) {
        this.reqDisk = reqDisk;
        return this;
    }

    public VmBuilder withResources(double cpu, double coreProcessingPower, long ram) {
        this.cpu = cpu;
        this.coreProcessingPower = coreProcessingPower;
        this.ram = ram;
        return this;
    }

    public VirtualMachine build(SimulationContext context) throws Exception {
        VirtualAppliance va = new VirtualAppliance(name, startupProcess, networkTraffic, false, reqDisk);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(cpu, coreProcessingPower, ram);

        context.getIaasContext().iaas.repositories.get(0).registerObject(va);
        VirtualMachine[] vms = context.getIaasContext().iaas.requestVM(va, arc,
                context.getIaasContext().iaas.repositories.get(0), 1);

        return vms[0];
    }
}
