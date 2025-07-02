package hu.digital_twin.context;

import hu.digital_twin.service.simulation.SimulationMetrics;

import java.util.HashMap;
import java.util.Map;

public class SimulationContext {
    // Az IaaS környezethez tartozó kontextus (fizikai gépek, VM-ek, energia stb.)
    private final IaaSContext iaasContext;

    // Virtuális gépekhez tartozó maximális másodpercenkénti utasításszámok (VM azonosító -> max utasítás)
    private final Map<String, Long> maxInstrPerSecond;

    // VM-ekhez tartozó fájlméretek (VM azonosító -> fájlméret megabyte-ban)
    private final Map<String, Integer> fileSizes;

    // A szimuláció során gyűjtött metrikák (energiafogyasztás, feladatok száma, átvitt adatok stb.)
    private final SimulationMetrics metrics;

    // Aktuálisan kezelendő virtuális gépek száma a szimulációban
    private int numberOfVms = 0;

    // Konstruktor, inicializálja az összetevőket
    public SimulationContext(IaaSContext iaasContext) {
        this.iaasContext = iaasContext;
        this.maxInstrPerSecond = new HashMap<>();
        this.fileSizes = new HashMap<>();
        this.metrics = new SimulationMetrics();
    }

    // Getter az IaaS kontextus eléréséhez
    public IaaSContext getIaasContext() { return iaasContext; }

    // Getter a VM-ek maximális másodpercenkénti utasításszámaihoz
    public Map<String, Long> getMaxInstrPerSecond() { return maxInstrPerSecond; }

    // Getter a VM-ek fájlméreteihez
    public Map<String, Integer> getFileSizes() { return fileSizes; }

    // Getter a szimulációs metrikák eléréséhez
    public SimulationMetrics getMetrics() { return metrics; }

    // Getter az aktuális virtuális gép számhoz
    public int getNumberOfVms() { return numberOfVms; }

    // VM számláló növelése, amikor új VM-et hozunk létre vagy kezelünk
    public void incrementVmCount() { this.numberOfVms++; }
}
