package hu.digital_twin.service.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.util.ResourceCalculationService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SimulationStatsService {

    private final ResourceCalculationService resourceCalculationService;

    public SimulationStatsService(ResourceCalculationService resourceCalculationService) {
        this.resourceCalculationService = resourceCalculationService;
    }

    /**
     * Szimuláció futásidejére vonatkozó statisztikákat generál JSON formátumban.
     *
     * @param runtime Futásidő milliszekundumban
     * @param lastUpdateData VM konfigurációs adatok
     * @param totalEnergyConsumption Összesített energiafogyasztás kWh-ban
     * @param totalMovedData Összesített adatátvitel MB-ban
     * @param vms Használt VM-ek száma
     * @param backUpVms Backup VM-ek térképe (lehet null baseline esetén)
     * @param totalTasks Szimulált taskok száma
     * @return JSON formátumú statisztikai összefoglaló
     */
    public String generateRuntimeStats(long runtime, RequestData lastUpdateData,
                                       double totalEnergyConsumption,
                                       int totalMovedData,
                                       int vms, Map<String, VirtualMachine> backUpVms,
                                       int totalTasks) {
        double hours = runtime / 3600000.0;
        double minutes = runtime / 60000.0;

        double iotCost = resourceCalculationService.calculateIoTCost(lastUpdateData, hours, backUpVms);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

            Map<String, Object> statsMap = new LinkedHashMap<>();
            statsMap.put("runtime_ms", runtime);
            statsMap.put("runtime_minutes", minutes);
            statsMap.put("runtime_hours", hours);
            statsMap.put("total_iot_cost_usd", iotCost);
            statsMap.put("total_energy_consumption_kwh", totalEnergyConsumption);
            statsMap.put("total_moved_data_mb", totalMovedData);
            statsMap.put("total_vm_tasks_simulated", totalTasks);
            statsMap.put("number_of_vms_utilized", vms);

            return objectMapper.writeValueAsString(statsMap);
        } catch (JsonProcessingException e) {
            return "Failed to generate JSON stats";
        }
    }
}

