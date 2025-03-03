package hu.digital_twin.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.RequestDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RequestDataService {

    @Autowired
    private RequestDataRepository requestDataRepository;

    public List<RequestData> getAllRequestData() {
        return requestDataRepository.findAll();
    }

    public RequestData getRequestDataById(Long id) {
        return requestDataRepository.findById(id).orElse(null);
    }

    public RequestData createRequestData(RequestData requestData) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = currentDateTime.format(formatter);
        requestData.setTimestamp(formattedDateTime);
        return requestDataRepository.save(requestData);
    }

    public void deleteRequestData(Long id) {
        requestDataRepository.deleteById(id);
    }

    public RequestData updateRequestData(Long id, RequestData requestData) {
        RequestData requestDataModified = requestDataRepository.findById(id).orElseThrow(() -> new RuntimeException("Requested data not found"));
        requestDataModified.setRequestType(requestData.getRequestType());
        requestDataModified.setVmsCount(requestData.getVmsCount());
        requestDataModified.setDataSinceLastSave(requestData.getDataSinceLastSave());

        requestDataModified.getVmData().clear();
        requestDataModified.getVmData().addAll(requestData.getVmData());

        return requestDataRepository.save(requestDataModified);
    }

    @Transactional
    public void deleteAllData() {
        requestDataRepository.deleteAll();
    }
}