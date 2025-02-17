package hu.digital_twin.service;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class SimulatorService {

    @Autowired
    private RequestDataService requestDataService;

    private final List<VirtualAppliance> virtualAppliances = new ArrayList<>();
    private final List<VirtualMachine> VMs = new ArrayList<>();
    private final List<Instance> instances = new ArrayList<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpHeaders headers = new HttpHeaders();
    Repository repo;
    PhysicalMachine pm;

    public SimulatorService() throws NetworkNode.NetworkException {
        /*long storageSize = 107_374_182_400L; // 100 GB
        long bandwidth = 12_500; // 100 Mbps

        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

        this.repo = new Repository(storageSize, "repo", bandwidth, bandwidth, bandwidth, new HashMap<String, Integer>(),
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        repo.setState(NetworkNode.State.RUNNING);

        this.pm = new PhysicalMachine(8, 1, 8589934592L, repo, 0, 10_000,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));
        pm.turnon();*/
    }

    public void handleRequest(RequestData requestData) throws VMManager.VMManagementException, NetworkNode.NetworkException {
        String request_type = requestData.getRequestType().toUpperCase();
        switch (request_type) {
            case "UPDATE":
                /*if (VMs.isEmpty()) {
                    for (VmData vmData : requestData.getVmInstances()) {
                        createVM(vmData);
                    }
                    for (VirtualMachine vm : VMs) {
                        System.out.println("First: " + vm.getResourceAllocation().allocated);
                    }
                    Timed.simulateUntilLastEvent();
                } else {
                    for (VmData vmData : requestData.getVmInstances()) {
                        VirtualMachine vm = getVM(vmData.getName());
                        if (checkDiff(vm, vmData)) {
                            vm.switchoff(true);
                            vm.switchOn(pm.allocateResources(new AlterableResourceConstraints(vmData.getCpu(),
                                    vmData.getCoreProcessingPower(), vmData.getRam()), true, PhysicalMachine.defaultAllocLen), repo);
                            System.out.println("Changed: ");
                        } else {
                            System.out.println("Not Changed");
                        }
                        System.out.println(vm.getResourceAllocation().allocated);
                    }
                    Timed.simulateUntilLastEvent();
                }*/
                requestDataService.createRequestData(requestData);
                List<RequestData> query = requestDataService.getAllRequestData();
                for(RequestData rd : query) {
                    System.out.println(rd.toString());
                }
                break;
            case "REQUEST PREDICTION":
                // TO DO call prediction functions
                break;
            default:
                System.err.println("Error: Unknown Request Type");
                break;
        }
    }

    public void createVM(VmData vmData) throws VMManager.VMManagementException, NetworkNode.NetworkException {
        createVirtualAppliance(vmData);
        repo.registerObject(getVA(vmData.getName()));
        VMs.add(pm.requestVM(getVA(vmData.getName()),
                new AlterableResourceConstraints(vmData.getCpu(), vmData.getCoreProcessingPower(), vmData.getRam()), repo, 1)[0]);
    }

    public VirtualAppliance getVA(String id) {
        for (VirtualAppliance va : virtualAppliances) {
            if (va.id.equals(id)) {
                return va;
            }
        }
        System.err.println("VirtualAppliance not found.");
        return null;
    }

    public VirtualMachine getVM(String id) {
        for (VirtualMachine vm : VMs) {
            if (vm.getVa().id.equals(id)) {
                return vm;
            }
        }
        System.err.println("VirtualMachine not found.");
        return null;
    }


    public void createVirtualAppliance(VmData vmData) {
        virtualAppliances.add(new VirtualAppliance(vmData.getName(), vmData.getStartupProcess(), 0, false, vmData.getReqDisk()));
    }

    public void createInstance(VmData vmData) {
        instances.add(new Instance(vmData.getName(),
                new VirtualAppliance(vmData.getName(), vmData.getStartupProcess(), 0, false, vmData.getReqDisk()),
                new AlterableResourceConstraints(vmData.getCpu(), vmData.getCoreProcessingPower(), vmData.getRam()), vmData.getPricePerTick()));
    }

    public String sendData(String data, String url) {

        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(data, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        return response.getBody();
    }

    public boolean checkDiff(VirtualMachine vm, VmData vi) {
        return vm.getResourceAllocation().allocated.getRequiredMemory() != vi.getRam() ||
                vm.getResourceAllocation().allocated.getRequiredCPUs() != vi.getCpu() ||
                vm.getResourceAllocation().allocated.getRequiredProcessingPower() != vi.getCoreProcessingPower();
    }
}

