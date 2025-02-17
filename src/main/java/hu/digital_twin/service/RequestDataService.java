package hu.digital_twin.service;

import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.RequestDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        return requestDataRepository.save(requestData);
    }

    public void deleteRequestData(Long id) {
        requestDataRepository.deleteById(id);
    }

    public RequestData updateRequestData(Long id, RequestData requestData) {
        RequestData requestDataModified = requestDataRepository.findById(id).orElseThrow(() -> new RuntimeException("Requested data not found"));
        requestDataModified.setRequestType(requestData.getRequestType());
        requestDataModified.setVmsCount(requestData.getVmsCount());
        requestDataModified.setTasksCount(requestData.getTasksCount());

        requestDataModified.getVmData().clear();
        requestDataModified.getVmData().addAll(requestData.getVmData());

        return requestDataRepository.save(requestDataModified);
    }
}