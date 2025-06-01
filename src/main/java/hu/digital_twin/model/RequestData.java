package hu.digital_twin.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
/*@Table(name = "request_data", indexes = {
        @Index(name = "idx_requestType", columnList = "requestType"),
        @Index(name = "idx_vmsCount", columnList = "vmsCount")
})*/
public class RequestData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String requestType;
    private int vmsCount;
    private String timestamp;
    @Transient
    private int basedOnLast;
    @Transient
    private int predictionLength; // in minutes
    @Transient
    private String featureName;

    @Transient
    private int simLength;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_data_id")
    private List<VmData> vmData;

    public int getSimLength() {
        return simLength;
    }

    public void setSimLength(int simLength) {
        this.simLength = simLength;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public int getBasedOnLast() {
        return basedOnLast;
    }

    public void setBasedOnLast(int basedOn) {
        this.basedOnLast = basedOn;
    }

    public int getPredictionLength() {
        return predictionLength;
    }

    public void setPredictionLength(int predictionLength) {
        this.predictionLength = predictionLength;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

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
                .append(", timestamp=").append(timestamp)
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

