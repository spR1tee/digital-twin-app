package hu.digital_twin.service.io;

import hu.digital_twin.config.DataSourceConfig;
import hu.digital_twin.context.TenantContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.RequestDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Szolgáltatás a RequestData entitások kezelésére, több bérlős (multi-tenant) környezetben.
 */
@Service
public class RequestDataService {

    private final RequestDataRepository requestDataRepository;

    private final DataSourceConfig dataSourceConfig;

    public RequestDataService(RequestDataRepository requestDataRepository, DataSourceConfig dataSourceConfig) {
        this.requestDataRepository = requestDataRepository;
        this.dataSourceConfig = dataSourceConfig;
    }

    /**
     * Visszaadja az összes RequestData rekordot.
     */
    public List<RequestData> getAllRequestData() {
        // Tenant adatforrás beállítása
        dataSourceConfig.createAndRegister(TenantContext.getTenantId());
        return requestDataRepository.findAll();
    }

    /**
     * Lekérdez egy RequestData rekordot azonosító alapján.
     */
    public RequestData getRequestDataById(Long id) {
        dataSourceConfig.createAndRegister(TenantContext.getTenantId());
        return requestDataRepository.findById(id).orElse(null);
    }

    /**
     * Új RequestData rekord létrehozása.
     */
    public void createRequestData(RequestData requestData) {
        dataSourceConfig.createAndRegister(TenantContext.getTenantId());

        // Aktuális timestamp beállítása
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = currentDateTime.format(formatter);
        requestData.setTimestamp(formattedDateTime);

        requestDataRepository.save(requestData);
    }

    /**
     * Egy RequestData törlése azonosító alapján.
     */
    public void deleteRequestData(Long id) {
        dataSourceConfig.createAndRegister(TenantContext.getTenantId());
        requestDataRepository.deleteById(id);
    }

    /**
     * RequestData frissítése adott ID-val.
     */
    public RequestData updateRequestData(Long id, RequestData requestData) {
        dataSourceConfig.createAndRegister(TenantContext.getTenantId());
        RequestData existingData = requestDataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Requested data not found"));

        // Mezők frissítése
        existingData.setRequestType(requestData.getRequestType());
        existingData.setVmsCount(requestData.getVmsCount());

        // VM adatok frissítése: először törlés, majd új adatok hozzáadása
        existingData.getVmData().clear();
        existingData.getVmData().addAll(requestData.getVmData());

        return requestDataRepository.save(existingData);
    }

    /**
     * Lekéri a legfrissebb RequestData rekordot.
     */
    public RequestData getLastData() {
        dataSourceConfig.createAndRegister(TenantContext.getTenantId());
        return requestDataRepository.findTopByOrderByIdDesc();
    }

    /**
     * Minden adat törlése egy tranzakcióban.
     */
    @Transactional
    public void deleteAllData() {
        dataSourceConfig.createAndRegister(TenantContext.getTenantId());
        requestDataRepository.deleteAll();
    }
}
