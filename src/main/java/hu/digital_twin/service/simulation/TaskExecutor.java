package hu.digital_twin.service.simulation;

import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.event.DataTransferEventHandler;
import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Feladatok végrehajtásáért felelős szolgáltatás
 *
 * Ez a szolgáltatás különböző típusú feladatok végrehajtását koordinálja
 * virtuális gépeken belül egy felhő szimulációs környezetben.
 */
@Service
public class TaskExecutor {

    /**
     * Nem skálázott feladatokat hajt végre egy adott VM-re
     *
     * Ez a metódus előre feldolgozott predikciós adatok alapján hajt végre
     * feladatokat anélkül, hogy automatikus skálázást alkalmazna.
     *
     * @param vm A virtuális gép, amelyen a feladatokat végre kell hajtani
     * @param predictionData Feldolgozott predikciós adatok, tartalmazzák a terheléseket és feladatokat
     * @param context Szimulációs kontextus, amely tartalmazza a globális állapotot
     * @throws NetworkNode.NetworkException Ha hálózati hiba történik a feladat végrehajtása során
     */
    public void executeNonScalingTasks(VirtualMachine vm, ProcessedPredictionData predictionData,
                                       SimulationContext context) throws NetworkNode.NetworkException {
        // VM egyedi azonosítójának lekérése
        String vmId = vm.getVa().id;

        // Átlagos terhelések és feladat utasítások lekérése az adott VM-hez
        List<Double> loads = predictionData.getAvgLoadsPerMinute().get(vmId);
        List<Integer> tasks = predictionData.getTaskInstructionsPerMinute().get(vmId);

        // Csak akkor hajtjuk végre a feladatokat, ha mindkét lista elérhető
        if (loads != null && tasks != null) {
            // Végigiterálunk minden percen és végrehajtjuk a megfelelő feladatokat
            for (int i = 0; i < loads.size(); i++) {
                executeTask(vm, loads.get(i), tasks.get(i), context, vmId);
            }
        }
    }

    /**
     * Baseline feladatok végrehajtása egy VM-en
     *
     * Ez a metódus alapvető terhelést szimulál egy VM-en, amely a legutóbbi
     * ismert használati adatok alapján számított.
     *
     * @param vm A virtuális gép, amelyen a baseline feladatokat végre kell hajtani
     * @param lastUpdateData Legutóbbi frissítési adatok, tartalmazzák a VM használati információkat
     * @param context Szimulációs kontextus
     * @param predictionLength A predikció hossza percekben
     * @throws NetworkNode.NetworkException Ha hálózati hiba történik
     */
    public void executeBaselineTasks(VirtualMachine vm, RequestData lastUpdateData,
                                     SimulationContext context, int predictionLength) throws NetworkNode.NetworkException {
        // VM adatok keresése a név alapján
        VmData vmData = findVmDataByName(vm.getVa().id, lastUpdateData);
        if (vmData == null) return; // Ha nem találjuk a VM adatokat, kilépünk

        // Végrehajtjuk a baseline feladatokat a predikció hosszának megfelelően
        for (int i = 0; i < predictionLength; i++) {
            // Baseline utasítások számának kiszámítása a VM aktuális használata alapján
            int instructions = calculateBaselineInstructions(vmData, context);
            // Feladat végrehajtása inverz terheléssel (1 - usage)
            executeTask(vm, 1 - vmData.getUsage(), instructions, context, vmData.getName());
        }
    }

    /**
     * Normál terhelésű VM-re egy feladat hozzárendelése
     *
     * Ez egy egyszerű wrapper metódus, amely egy feladatot hajt végre
     * egy VM-en normál körülmények között.
     *
     * @param vm A virtuális gép
     * @param vmId A VM azonosítója
     * @param load A terhelés mértéke (0.0 - 1.0 között)
     * @param task A feladat utasításainak száma
     * @param context Szimulációs kontextus
     * @throws NetworkNode.NetworkException Ha hálózati hiba történik
     */
    public void executeNormalTask(VirtualMachine vm, String vmId, double load, int task,
                                  SimulationContext context) throws NetworkNode.NetworkException {
        executeTask(vm, load, task, context, vmId);
    }

    /**
     * Feladat elosztása egy VM és egy backup VM között
     *
     * Ez a metódus egy feladatot oszt el két VM között terheléselosztás céljából.
     * A terhelést és a feladatot a LOAD_SPLIT_RATIO szerint osztja fel.
     *
     * @param originalVm Az eredeti virtuális gép
     * @param backupVm A backup virtuális gép
     * @param load A teljes terhelés
     * @param task A teljes feladat utasításainak száma
     * @param context Szimulációs kontextus
     * @param vmId A VM azonosítója
     * @throws NetworkNode.NetworkException Ha hálózati hiba történik
     */
    public void distributeTaskBetweenVms(VirtualMachine originalVm, VirtualMachine backupVm,
                                         double load, int task, SimulationContext context,
                                         String vmId) throws NetworkNode.NetworkException {
        // Terhelés és feladat felezése a konstans arány szerint
        double halfLoad = Math.round(load * SimulationConstants.LOAD_SPLIT_RATIO);
        long halfTask = Math.round(task * SimulationConstants.LOAD_SPLIT_RATIO);

        // Feladat végrehajtása az eredeti VM-en
        originalVm.newComputeTask(halfTask, 1 - halfLoad,
                createDataTransferHandler(context, originalVm, vmId));

        // Feladat végrehajtása a backup VM-en
        backupVm.newComputeTask(halfTask, 1 - halfLoad,
                createDataTransferHandler(context, backupVm, vmId));

        // Metrikák frissítése a teljes feladat mennyiségével
        context.getMetrics().addTasks(task);
    }

    /**
     * Privát metódus egy feladat végrehajtásához egy VM-en
     *
     * Ez a központi metódus, amely ténylegesen végrehajtja a feladatokat.
     * Normalizálja a terhelést és létrehozza a szükséges event handlereket.
     *
     * @param vm A virtuális gép
     * @param load A terhelés (normalizálásra kerül)
     * @param task A feladat utasításainak száma
     * @param context Szimulációs kontextus
     * @param vmId A VM azonosítója
     * @throws NetworkNode.NetworkException Ha hálózati hiba történik
     */
    private void executeTask(VirtualMachine vm, double load, int task, SimulationContext context, String vmId)
            throws NetworkNode.NetworkException {
        // Terhelés normalizálása a megengedett tartományba (MIN_LOAD és MAX_LOAD között)
        double normalizedLoad = Math.max(SimulationConstants.MIN_LOAD,
                Math.min(SimulationConstants.MAX_LOAD, load));

        // Új számítási feladat indítása a VM-en
        // A második paraméter (1 - normalizedLoad) a rendelkezésre álló CPU kapacitást jelenti
        vm.newComputeTask(task, 1 - normalizedLoad,
                createDataTransferHandler(context, vm, vmId));

        // Metrikák frissítése a végrehajtott feladatok számával
        context.getMetrics().addTasks(task);
    }

    /**
     * VM adatok keresése név alapján
     *
     * @param vmName A keresett VM neve
     * @param requestData A kérés adatok, amelyek tartalmazzák a VM listát
     * @return A talált VmData objektum vagy null, ha nem található
     */
    private VmData findVmDataByName(String vmName, RequestData requestData) {
        return requestData.getVmData().stream()
                .filter(vd -> vd.getName().equals(vmName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Baseline utasítások számának kiszámítása
     *
     * A számítás a VM aktuális használati százaléka és a maximális
     * utasítás/másodperc érték alapján történik.
     *
     * @param vmData A VM adatok
     * @param context Szimulációs kontextus
     * @return A kiszámított utasítások száma
     */
    private int calculateBaselineInstructions(VmData vmData, SimulationContext context) {
        return (int) Math.round(SimulationConstants.SECONDS_PER_MINUTE *
                (vmData.getUsage() / 100.0) * context.getMaxInstrPerSecond().get(vmData.getName()));
    }

    /**
     * Adatátviteli event handler létrehozása
     *
     * Ez a metódus létrehoz egy event handlert, amely az adatátviteli műveleteket
     * kezeli a feladat végrehajtása során.
     *
     * @param context Szimulációs kontextus
     * @param vm A virtuális gép
     * @param vmId A VM azonosítója
     * @return Új DataTransferEventHandler példány
     */
    private DataTransferEventHandler createDataTransferHandler(SimulationContext context, VirtualMachine vm, String vmId) {
        return new DataTransferEventHandler(
                context.getIaasContext(),           // IaaS kontextus
                vm,                                 // Virtuális gép
                context.getFileSizes().get(vmId),   // Fájl méretek a VM-hez
                // Lambda kifejezés, amely frissíti a metrikákat amikor adatátvitel történik
                () -> context.getMetrics().addMovedData(context.getFileSizes().get(vmId))
        );
    }
}