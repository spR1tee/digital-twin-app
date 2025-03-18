package hu.digital_twin.service;

import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class SimulatorService {

    @Autowired
    private RequestDataService requestDataService;

    private final List<VirtualAppliance> virtualAppliances = new ArrayList<>();
    private final List<VirtualMachine> VMs = new ArrayList<>();
    private final List<Instance> instances = new ArrayList<>();
    Repository repo;
    PhysicalMachine pm;

    public SimulatorService() {}

    public void handleRequest(RequestData requestData) throws VMManager.VMManagementException, NetworkNode.NetworkException, IOException, InterruptedException {
        String request_type = requestData.getRequestType().toUpperCase();
        switch (request_type) {
            case "UPDATE":
                requestDataService.createRequestData(requestData);
                /*List<RequestData> query = requestDataService.getAllRequestData();
                for(RequestData rd : query) {
                    System.out.println(rd.toString());
                }*/
                break;
            case "REQUEST PREDICTION":
                try {
                    String scriptPath = "src/main/resources/scripts/linear_regression_model.py";
                    ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, requestData.getFeatureName(), Integer.toString(requestData.getBasedOnLast()), Integer.toString(requestData.getPredictionLength()));
                    processBuilder.redirectErrorStream(true);
                    Process process = processBuilder.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        sendData(line, "http://localhost:8082/dummy/receiveData");
                    }

                    int exitCode = process.waitFor();
                    System.out.println("Python script exited with code: " + exitCode);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
            case "REQUEST FUTURE BEHAVIOUR":
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

    public void sendData(String data, String url) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>(data, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        //System.out.println(response.getBody());
    }

    public boolean checkDiff(VirtualMachine vm, VmData vi) {
        return vm.getResourceAllocation().allocated.getRequiredMemory() != vi.getRam() ||
                vm.getResourceAllocation().allocated.getRequiredCPUs() != vi.getCpu() ||
                vm.getResourceAllocation().allocated.getRequiredProcessingPower() != vi.getCoreProcessingPower();
    }
}

