package hu.digital_twin.service.simulation;

public class SimulationMetrics {
    private double totalEnergyConsumption = 0.0;
    private int totalMovedData = 0;
    private int totalTasks = 0;

    public void reset() {
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
    }

    public void addEnergyConsumption(double energy) { this.totalEnergyConsumption += energy; }
    public void addMovedData(int data) { this.totalMovedData += data; }
    public void addTasks(int tasks) { this.totalTasks += tasks; }

    public double getTotalEnergyConsumption() { return totalEnergyConsumption; }
    public int getTotalMovedData() { return totalMovedData; }
    public int getTotalTasks() { return totalTasks; }
}
