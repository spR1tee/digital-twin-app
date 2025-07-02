package hu.digital_twin.service.io;

import hu.digital_twin.model.VmData;
import hu.digital_twin.model.VmDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A VmData entitások adatbázis műveleteit végző szolgáltatás.
 */
@Service
public class VmDataService {

    private final VmDataRepository vmDataRepository;

    public VmDataService(VmDataRepository vmDataRepository) {
        this.vmDataRepository = vmDataRepository;
    }

    /**
     * Az összes VM adat lekérdezése.
     */
    public List<VmData> getAllVmData() {
        return vmDataRepository.findAll();
    }

    /**
     * Egy adott VM rekord lekérdezése ID alapján.
     */
    public VmData getVmDataById(Long id) {
        return vmDataRepository.findById(id).orElse(null);
    }

    /**
     * Új VM rekord létrehozása.
     */
    public VmData createVmData(VmData vmData) {
        return vmDataRepository.save(vmData);
    }

    /**
     * VM rekord törlése ID alapján.
     */
    public void deleteVmData(Long id) {
        vmDataRepository.deleteById(id);
    }
}
