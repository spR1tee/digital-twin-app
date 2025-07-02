package hu.digital_twin.service.simulation;

import hu.digital_twin.config.SimulationConfig;
import hu.digital_twin.context.SimulationContext;
import hu.digital_twin.exception.SimulationException;
import hu.digital_twin.model.ProcessedPredictionData;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.service.io.RequestDataService;
import hu.digital_twin.service.prediction.PredictionDataProcessor;
import hu.digital_twin.service.prediction.PredictionService;
import hu.digital_twin.strategy.BaselineSimulationStrategy;
import hu.digital_twin.strategy.PredictionWithScalingStrategy;
import hu.digital_twin.strategy.PredictionWithoutScalingStrategy;
import hu.digital_twin.strategy.SimulationStrategy;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refaktorált szimuláció szolgáltatás
 * A felelősségek külön komponensekbe vannak kiszervezve a jobb karbantarthatóság érdekében
 */
@Service
public class SimulationService {

    private final RequestDataService requestDataService;
    private final PredictionService predictionService;
    private final SimulationConfig config;
    private final Map<String, SimulationStrategy> strategies;

    // Kiszervezett komponensek
    private final SimulationLifecycleManager lifecycleManager;
    private final PredictionDataProcessor dataProcessor;
    private final TaskExecutor taskExecutor;
    private final ScalingManager scalingManager;

    public SimulationService(RequestDataService requestDataService,
                             PredictionService predictionService,
                             SimulationConfig config,
                             SimulationLifecycleManager lifecycleManager,
                             PredictionDataProcessor dataProcessor,
                             TaskExecutor taskExecutor,
                             ScalingManager scalingManager,
                             @Lazy BaselineSimulationStrategy baselineStrategy,
                             @Lazy PredictionWithoutScalingStrategy withoutScalingStrategy,
                             @Lazy PredictionWithScalingStrategy withScalingStrategy) {
        this.requestDataService = requestDataService;
        this.predictionService = predictionService;
        this.config = config;
        this.lifecycleManager = lifecycleManager;
        this.dataProcessor = dataProcessor;
        this.taskExecutor = taskExecutor;
        this.scalingManager = scalingManager;

        this.strategies = Map.of(
                "baseline", baselineStrategy,
                "prediction_no_scaling", withoutScalingStrategy,
                "prediction_with_scaling", withScalingStrategy
        );
    }

    /**
     * Elindítja a predikciót skálázás nélkül
     */
    public String usePredictionWithoutScaling(RequestData currentRequestData) throws SimulationException {
        return strategies.get("prediction_no_scaling").execute(currentRequestData);
    }

    /**
     * Elindítja a predikciót skálázással
     */
    public String usePredictionWithScaling(RequestData currentRequestData) throws SimulationException {
        return strategies.get("prediction_with_scaling").execute(currentRequestData);
    }

    /**
     * Lefuttatja az alap baseline predikciót
     */
    public String doBaseline(RequestData currentRequestData) throws SimulationException {
        return strategies.get("baseline").execute(currentRequestData);
    }

    /**
     * Inicializálja a szimulációt adott számú fizikai géphez
     */
    public SimulationContext initializeSimulation(int physicalMachineCount) throws Exception {
        return lifecycleManager.initializeSimulation(physicalMachineCount);
    }

    /**
     * Elindítja a szimulációs események futását
     */
    public void startSimulation(SimulationContext context) {
        lifecycleManager.startSimulation(context);
    }

    /**
     * Lefuttatja a szimulációt adott percig
     */
    public void runSimulation(int minutes) {
        lifecycleManager.runSimulation(minutes);
    }

    /**
     * Szimuláció lezárása és statisztikák generálása
     */
    public String finalizeSimulation(SimulationContext context, RequestData lastUpdateData,
                                     Map<String, VirtualMachine> backUpVms) {
        return lifecycleManager.finalizeSimulation(context, lastUpdateData, backUpVms);
    }


    /**
     * Előkészíti a predikciós adatokat percenkénti feldolgozásra
     */
    public ProcessedPredictionData processPerMinuteData(Map<String, List<Double>> predictionData,
                                                        SimulationContext context) {
        return dataProcessor.processPerMinuteData(predictionData, context);
    }


    /**
     * Nem skálázott feladatokat hajt végre egy adott VM-re
     */
    public void executeNonScalingTasks(VirtualMachine vm, ProcessedPredictionData predictionData,
                                       SimulationContext context) throws NetworkNode.NetworkException {
        taskExecutor.executeNonScalingTasks(vm, predictionData, context);
    }

    /**
     * Baseline feladatok végrehajtása egy VM-en
     */
    public void executeBaselineTasks(VirtualMachine vm, RequestData lastUpdateData,
                                     SimulationContext context, int predictionLength) throws NetworkNode.NetworkException {
        taskExecutor.executeBaselineTasks(vm, lastUpdateData, context, predictionLength);
    }


    /**
     * Skálázott feladatok végrehajtása
     */
    public int executeScalingTasks(SimulationContext context, ProcessedPredictionData predictionData,
                                   RequestData lastUpdateData, Map<String, VirtualMachine> backUpVms,
                                   double loadThreshold) throws Exception {
        return scalingManager.executeScalingTasks(context, predictionData, lastUpdateData, backUpVms, loadThreshold);
    }

    /**
     * Az összes VM listázása egy kontextusban
     */
    public List<VirtualMachine> getAllVirtualMachines(SimulationContext context) {
        List<VirtualMachine> allVms = new ArrayList<>();
        for (PhysicalMachine pm : context.getIaasContext().iaas.machines) {
            allVms.addAll(pm.listVMs());
        }
        return allVms;
    }

    /**
     * VM keresése név alapján
     */
    public VirtualMachine findVirtualMachineByName(SimulationContext context, String vmName) {
        return getAllVirtualMachines(context).stream()
                .filter(vm -> vm.getVa().id.equals(vmName))
                .findFirst()
                .orElse(null);
    }

    // === Getterek a külső hozzáféréshez ===

    public RequestDataService getRequestDataService() {
        return requestDataService;
    }

    public PredictionService getPredictionService() {
        return predictionService;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public long getStarttime() {
        return lifecycleManager.getStarttime();
    }

    public void setStarttime(long starttime) {
        lifecycleManager.setStarttime(starttime);
    }

    public long getStoptime() {
        return lifecycleManager.getStoptime();
    }

    public void setStoptime(long stoptime) {
        lifecycleManager.setStoptime(stoptime);
    }

    // === Belső komponensek elérése (ha szükséges) ===

    public SimulationLifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    public PredictionDataProcessor getDataProcessor() {
        return dataProcessor;
    }

    public TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    public ScalingManager getScalingManager() {
        return scalingManager;
    }
}