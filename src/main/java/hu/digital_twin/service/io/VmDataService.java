package hu.digital_twin.service.io;

import hu.digital_twin.model.VmData;
import hu.digital_twin.model.VmDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VmDataService {

    @Autowired
    private VmDataRepository vmDataRepository;

    public List<VmData> getAllVmData() {
        return vmDataRepository.findAll();
    }

    public VmData getVmDataById(Long id) {
        return vmDataRepository.findById(id).orElse(null);
    }

    public VmData createVmData(VmData vmData) {
        return vmDataRepository.save(vmData);
    }

    public void deleteVmData(Long id) {
        vmDataRepository.deleteById(id);
    }
}
