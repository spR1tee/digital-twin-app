package hu.digital_twin.service.simulation;

/**
 * A szimuláció során mért és összegzett metrikák nyilvántartására szolgáló osztály.
 * Tárolja az összesített energiafogyasztást, mozgatott adatmennyiséget és feldolgozott feladatok számát.
 */
public class SimulationMetrics {

    // Teljes energiafogyasztás kilowattórában (kWh)
    private double totalEnergyConsumption = 0.0;

    // Összes áthelyezett adat mennyisége (MB)
    private int totalMovedData = 0;

    // Összes végrehajtott feladat száma
    private int totalTasks = 0;

    /**
     * Az összes metrika nullázása – új szimulációhoz használatos.
     */
    public void reset() {
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
    }

    /**
     * Energiafogyasztás hozzáadása az aktuális összeghez.
     * @param energy fogyasztott energia (kWh)
     */
    public void addEnergyConsumption(double energy) {
        this.totalEnergyConsumption += energy;
    }

    /**
     * Mozgatott adat mennyiségének hozzáadása.
     * @param data átvitt adatmennyiség (MB)
     */
    public void addMovedData(int data) {
        this.totalMovedData += data;
    }

    /**
     * Feldolgozott feladatok számának növelése.
     * @param tasks hozzáadandó feladatszám
     */
    public void addTasks(int tasks) {
        this.totalTasks += tasks;
    }

    /**
     * Lekérdezi a szimuláció teljes energiafogyasztását.
     * @return energiafogyasztás (kWh)
     */
    public double getTotalEnergyConsumption() {
        return totalEnergyConsumption;
    }

    /**
     * Lekérdezi a teljes mozgatott adat mennyiségét.
     * @return adatmennyiség (MB)
     */
    public int getTotalMovedData() {
        return totalMovedData;
    }

    /**
     * Lekérdezi az összes végrehajtott feladat számát.
     * @return feladatok száma
     */
    public int getTotalTasks() {
        return totalTasks;
    }
}
