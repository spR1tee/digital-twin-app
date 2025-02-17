package hu.digital_twin.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
public class RequestData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String requestType;
    private int vmsCount;
    private long tasksCount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_data_id")
    private List<VmData> vmData;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getVmsCount() {
        return vmsCount;
    }

    public void setVmsCount(int vmsCount) {
        this.vmsCount = vmsCount;
    }

    public long getTasksCount() {
        return tasksCount;
    }

    public void setTasksCount(long tasksCount) {
        this.tasksCount = tasksCount;
    }

    public List<VmData> getVmData() {
        return vmData;
    }

    public void setVmData(List<VmData> vmData) {
        this.vmData = vmData;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RequestData{")
                .append("id=").append(id)
                .append(", requestType='").append(requestType).append('\'')
                .append(", vmsCount=").append(vmsCount)
                .append(", tasksCount=").append(tasksCount)
                .append(", vmData=[");

        if (vmData != null) {
            for (int i = 0; i < vmData.size(); i++) {
                sb.append(vmData.get(i));
                if (i < vmData.size() - 1) {
                    sb.append(", ");
                }
            }
        }

        sb.append("]}");
        return sb.toString();
    }
}

