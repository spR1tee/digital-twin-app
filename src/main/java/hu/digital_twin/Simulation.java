package hu.digital_twin;

import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.digital_twin.service.RequestDataService;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Component
@Scope("prototype")
public class Simulation {
    @Autowired
    private RequestDataService requestDataService;

    public void do_baseline() {
        try {
            SimLogger.setLogging(1, true);
            IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);

            // machines
            final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

            Repository pmRepo1 = new Repository(107_374_182_400L, "pmRepo1", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm1 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo1, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            Repository pmRepo2 = new Repository(107_374_182_400L, "pmRepo2", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm2 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo2, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            iaas.registerHost(pm1);
            iaas.registerHost(pm2);

            // repositories
            Repository cloudRepo = new Repository(107_374_182_400L, "cloudRepo", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            iaas.registerRepository(cloudRepo);

            cloudRepo.addLatencies("pmRepo1", 100);
            cloudRepo.addLatencies("pmRepo2", 125);
            pmRepo1.addLatencies("cloudRepo", 100);
            pmRepo2.addLatencies("cloudRepo", 100);


            // VMs
            RequestData rd = requestDataService.getLastData();
            for (VmData vd : rd.getVmData()) {
                VirtualAppliance va = new VirtualAppliance(vd.getName(), vd.getStartupProcess(), vd.getNetworkTraffic(), false, vd.getReqDisk());
                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());
                iaas.repositories.get(0).registerObject(va);
                iaas.requestVM(va, arc, iaas.repositories.get(0), 1);
            }

            Timed.simulateUntilLastEvent();
            long starttime = System.nanoTime();

            for (PhysicalMachine pm : iaas.machines) {
                System.out.println(pm);
                for (VirtualMachine vm : pm.listVMs()) {
                    System.out.println("\t" + vm);
                    for (VmData vd : rd.getVmData()) {
                        if (vd.getName().equals(vm.getVa().id)) {
                            vm.newComputeTask(14_285, vd.getUsage(), new ConsumptionEventAdapter() {
                                @Override
                                public void conComplete() {
                                    System.out.println("Completed_Time: " + Timed.getFireCount());
                                }
                            });
                        }
                    }
                }
                for (StorageObject content : pm.localDisk.contents()) {
                    System.out.println("\t" + content);
                }
            }
            for (Repository r : iaas.repositories) {
                System.out.println(r);
                for (StorageObject content : r.contents()) {
                    System.out.println("\t" + content);
                }
            }

            Timed.simulateUntilLastEvent();
            long stoptime = System.nanoTime();

            ScenarioBase.calculateIoTCost();
            ScenarioBase.logBatchProcessing(stoptime - starttime);

            System.out.println("Time: " + Timed.getFireCount());
            Timed.resetTimed();


        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException |
                 VMManager.VMManagementException e) {
            e.printStackTrace();
        } catch (NetworkNode.NetworkException e) {
            throw new RuntimeException(e);
        }
    }

    public void do_alternative(String mode) {
        try {
            SimLogger.setLogging(1, true);
            IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);

            // machines
            final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

            Repository pmRepo1 = new Repository(107_374_182_400L, "pmRepo1", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm1 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo1, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            Repository pmRepo2 = new Repository(107_374_182_400L, "pmRepo2", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm2 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo2, 0, 10_000, transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            iaas.registerHost(pm1);
            iaas.registerHost(pm2);

            // repositories
            Repository cloudRepo = new Repository(107_374_182_400L, "cloudRepo", 12_500, 12_500, 12_500, new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            iaas.registerRepository(cloudRepo);

            cloudRepo.addLatencies("pmRepo1", 100);
            cloudRepo.addLatencies("pmRepo2", 125);
            pmRepo1.addLatencies("cloudRepo", 100);
            pmRepo2.addLatencies("cloudRepo", 100);

            // VMs
            RequestData rd = requestDataService.getLastData();
            double usage = 0;
            int networkTraffic = 0;
            int numberOfVms;
            if (rd.getVmData().size() > 1) {
                if (mode.equals("down")) {
                    numberOfVms = rd.getVmData().size() - 1;
                } else if (mode.equals("up")) {
                    numberOfVms = rd.getVmData().size() + 1;
                } else {
                    return;
                }
                for (VmData vd : rd.getVmData()) {
                    networkTraffic += vd.getNetworkTraffic();
                    usage += vd.getUsage();
                }
                VmData tmp = rd.getVmData().get(0);
                VirtualAppliance va = new VirtualAppliance(tmp.getName(), tmp.getStartupProcess(), networkTraffic / numberOfVms, false, tmp.getReqDisk());
                AlterableResourceConstraints arc = new AlterableResourceConstraints(tmp.getCpu(), tmp.getCoreProcessingPower(), tmp.getRam());
                iaas.repositories.get(0).registerObject(va);
                iaas.requestVM(va, arc, iaas.repositories.get(0), numberOfVms);

                Timed.simulateUntilLastEvent();
                long starttime = System.nanoTime();

                for (PhysicalMachine pm : iaas.machines) {
                    System.out.println(pm);
                    for (VirtualMachine vm : pm.listVMs()) {
                        System.out.println("\t" + vm);
                        vm.newComputeTask(100_000 / numberOfVms, usage / numberOfVms, new ConsumptionEventAdapter() {
                            @Override
                            public void conComplete() {
                                System.out.println("Completed_Time: " + Timed.getFireCount());
                            }
                        });
                    }
                    for (StorageObject content : pm.localDisk.contents()) {
                        System.out.println("\t" + content);
                    }
                }
                for (Repository r : iaas.repositories) {
                    System.out.println(r);
                    for (StorageObject content : r.contents()) {
                        System.out.println("\t" + content);
                    }
                }

                Timed.simulateUntilLastEvent();
                long stoptime = System.nanoTime();

                ScenarioBase.calculateIoTCost();
                ScenarioBase.logBatchProcessing(stoptime - starttime);
                System.out.println("Time: " + Timed.getFireCount());
                Timed.resetTimed();
            }

        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 VMManager.VMManagementException | NetworkNode.NetworkException e) {
            throw new RuntimeException(e);
        }
    }
}


