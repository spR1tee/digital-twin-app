package hu.digital_twin.model;

import jakarta.persistence.*;

@Entity
public class VmData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int cpu;
    private long ram;
    private double coreProcessingPower;
    private int startupProcess;
    private long reqDisk;
    private double pricePerTick;
    private String status;
    private String type;
    private int networkTraffic; // in kb/sec
    private double usage;

    private int dataSinceLastSave; // in MBs

    public VmData() {}

    public int getDataSinceLastSave() {
        return dataSinceLastSave;
    }

    public void setDataSinceLastSave(int dataSinceLastSave) {
        this.dataSinceLastSave = dataSinceLastSave;
    }

    public int getNetworkTraffic() {
        return networkTraffic;
    }

    public void setNetworkTraffic(int network_traffic) {
        this.networkTraffic = network_traffic;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getUsage() {
        return usage;
    }

    public void setUsage(double usage) {
        this.usage = usage;
    }

    public long getReqDisk() {
        return reqDisk;
    }

    public void setReqDisk(long reqDisk) {
        this.reqDisk = reqDisk;
    }

    public int getStartupProcess() {
        return startupProcess;
    }

    public void setStartupProcess(int startupProcess) {
        this.startupProcess = startupProcess;
    }

    public double getPricePerTick() {
        return pricePerTick;
    }

    public void setPricePerTick(double pricePerTick) {
        this.pricePerTick = pricePerTick;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public long getRam() {
        return ram;
    }

    public void setRam(long ram) {
        this.ram = ram;
    }

    public double getCoreProcessingPower() {
        return coreProcessingPower;
    }

    public void setCoreProcessingPower(double coreProcessingPower) {
        this.coreProcessingPower = coreProcessingPower;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "VmData{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", cpu=" + cpu +
                ", ram=" + ram +
                ", coreProcessingPower=" + coreProcessingPower +
                ", startupProcess=" + startupProcess +
                ", reqDisk=" + reqDisk +
                ", pricePerTick=" + pricePerTick +
                ", status='" + status + '\'' +
                ", type='" + type + '\'' +
                ", networkTraffic=" + networkTraffic +
                ", usage=" + usage +
                '}';
    }
}

