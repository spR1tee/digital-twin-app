package hu.digital_twin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hu.digital_twin.context.TenantContext;
import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.digital_twin.service.RequestDataService;
import hu.digital_twin.service.Transfer;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cloud infrastruktúra szimulációs osztály.
 * Virtuális gépek terheléselosztását és energiafogyasztását, illetve költségét szimulálja predikciós adatok alapján.
 * Prototype scope-pal rendelkezik, így minden használatkor új példány jön létre.
 */
@Component
@Scope("prototype")
public class Simulation {

    // Kérési adatok kezelésére szolgáló service automatikus injektálása
    @Autowired
    private RequestDataService requestDataService;

    // Szimuláció során mért teljesítménymutatók
    private double totalEnergyConsumption = 0.0;
    private int totalMovedData = 0;
    private int totalTasks = 0;

    // Időzítési konstansok a predikciós adatok feldolgozásához
    private static final int SECONDS_PER_READING = 5;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int READINGS_PER_MINUTE = SECONDS_PER_MINUTE / SECONDS_PER_READING;

    /**
     * Alapértelmezett konstruktor.
     * Spring által használt dependency injection miatt üres.
     */
    public Simulation() {
    }

    /**
     * IaaS (Infrastructure as a Service) környezet konfigurációját tároló belső osztály.
     * Egy komplett cloud infrastruktúra szimulációs környezetet reprezentál.
     */
    public static class IaaSContext {
        public IaaSService iaas;
        public List<PhysicalMachine> pms = new ArrayList<>();
        public List<Repository> pmRepos = new ArrayList<>();
        public Repository cloudRepo;

        private final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions;

        /**
         * IaaSContext konstruktor.
         *
         * @param iaas A fő IaaS szolgáltatás példány
         * @param cloudRepo Központi cloud repository
         * @param transitions Áramfogyasztási állapotátmenetek konfigurációja
         */
        public IaaSContext(IaaSService iaas,
                           Repository cloudRepo,
                           EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions) {
            this.iaas = iaas;
            this.cloudRepo = cloudRepo;
            this.transitions = transitions;
        }

        /**
         * Fizikai gépek dinamikus hozzáadása a cloud infrastruktúrához.
         * Minden géphez létrehoz egy saját repository-t és beállítja a hálózati kapcsolatokat.
         *
         * @param count A létrehozandó fizikai gépek száma
         * @throws NetworkNode.NetworkException Hálózati konfigurációs hiba esetén
         */
        public void addPhysicalMachines(int count) throws NetworkNode.NetworkException {
            // Végigiterálunk a létrehozandó gépek számán és létrehozunk egyedi indexet és nevet
            for (int i = 0; i < count; i++) {
                int index = pms.size() + 1;
                String repoName = "pmRepo" + index;

                // Repository létrehozása az új fizikai géphez
                Repository repo = new Repository(
                        107_374_182_400L, repoName,
                        12_500, 12_500, 12_500,
                        new HashMap<>(),
                        transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                        transitions.get(PowerTransitionGenerator.PowerStateKind.network)
                );
                // Repository állapotának beállítása futó módra
                repo.setState(NetworkNode.State.RUNNING);

                // Fizikai gép létrehozása a repository-val
                PhysicalMachine pm = new PhysicalMachine(
                        8, 1, 17_179_869_184L,
                        repo, 0, 0,
                        transitions.get(PowerTransitionGenerator.PowerStateKind.host)
                );

                // Fizikai gép regisztrálása az IaaS rendszerben
                iaas.registerHost(pm);

                // Új gép hozzáadása a helyi listához
                pms.add(pm);

                // Új repository hozzáadása a repository listához
                pmRepos.add(repo);

                // Ha létezik központi cloud repository, hálózati késleltetés beállítása
                if (cloudRepo != null) {
                    // 100ms késleltetés beállítása cloud repo és az új repo között
                    cloudRepo.addLatencies(repoName, 100);
                    repo.addLatencies("cloudRepo", 100);
                }

                // Hálózati késleltetések beállítása az összes többi repository-val
                for (Repository other : pmRepos) {
                    // Csak a különböző repository-k között állítunk be késleltetést
                    if (!other.getName().equals(repo.getName())) {
                        repo.addLatencies(other.getName(), 100);
                        other.addLatencies(repo.getName(), 100);
                    }
                }
            }
        }
    }

    /**
     * Inicializálja az IaaS környezetet a megadott számú fizikai géppel
     *
     * @param initialPMCount A kezdetben létrehozandó fizikai gépek száma
     * @return Visszaadja a létrehozott és inicializált Iaas kontextust
     */
    public IaaSContext initializeIaaS(int initialPMCount) throws Exception {
        // Létrehoz egy IaaSService példányt, megadva az erőforrás-ütemezőt és a gépek energiamenedzsmentjét
        IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);

        // Létrehozza az energiamódosulások (pl. tárolás és hálózat) szimulációjához szükséges állapotváltásokat
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

        // Létrehoz egy központi tárhelyet (repository), amelynek van energiamodellje is
        Repository cloudRepo = new Repository(
                107_374_182_400L, "cloudRepo",
                12_500, 12_500, 12_500,
                new HashMap<>(),
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)
        );

        // A tárhelyet "RUNNING" állapotba helyezi (elérhető)
        cloudRepo.setState(NetworkNode.State.RUNNING);

        // Regisztrálja a repository-t az IaaS környezetben
        iaas.registerRepository(cloudRepo);

        // Létrehozza az IaaS kontextust, amely tartalmazza az IaaS-t, a repository-t és az átmeneti állapotokat
        IaaSContext context = new IaaSContext(iaas, cloudRepo, transitions);

        // Hozzáadja a megadott számú fizikai gépet a kontextushoz
        context.addPhysicalMachines(initialPMCount);

        return context;
    }

    // Egy eseményfigyelő osztály, amely adatátvitelt indít, amikor egy VM-en taskok szimulációja befejeződik
    public class DataTransferOnConsumption extends ConsumptionEventAdapter {

        private final VirtualMachine vm;
        private final IaaSContext context;
        private final int fileSize;

        public DataTransferOnConsumption(IaaSContext context, VirtualMachine vm, int fileSize) {
            this.context = context;
            this.vm = vm;
            this.fileSize = fileSize;
        }

        @Override
        public void conComplete() {
            Repository source = null;
            Repository target = null;

            // Megkeresi, melyik fizikai gépen fut a VM és lekéri annak repository-ját
            for (int i = 0; i < context.pms.size(); i++) {
                if (context.pms.get(i).listVMs().contains(vm)) {
                    source = context.pmRepos.get(i);

                    // Kiválaszt egy másik repository-t célként (az első nem egyező indexűt)
                    for (int j = 0; j < context.pmRepos.size(); j++) {
                        if (j != i) {
                            target = context.pmRepos.get(j);
                            break;
                        }
                    }
                    break;
                }
            }

            try {
                String fileName = "data";
                // Létrehoz egy adatátvitelt a forrás és cél repository között
                new Transfer(source, target, new StorageObject(fileName, fileSize, false));
                // Növeli az összes átvitt adat mennyiségét
                totalMovedData += fileSize;
            } catch (NetworkNode.NetworkException ex) {
                throw new RuntimeException("Transfer failed", ex);
            }
        }
    }

    // Energiaadat-gyűjtők beállítása minden fizikai gépre
    public void setupEDC(IaaSContext context) {
        int index = 1;
        for (PhysicalMachine pm : context.iaas.machines) {
            String name = "pm-" + index++;
            new EnergyDataCollector(name, pm, true);
        }
    }

    // Leállítja az összes energiaadat-gyűjtőt és összegzi a fogyasztást
    public void stopEDC() {
        for (EnergyDataCollector edc : EnergyDataCollector.energyCollectors) {
            totalEnergyConsumption += edc.energyConsumption / 1000 / 3_600_000;
            edc.stop();
        }
        // Törli a gyűjtők listáját
        EnergyDataCollector.energyCollectors.clear();
    }

    public String usePredictionWithoutScaling(RequestData currentRequestData) {
        // Globális teljesítménymutatók nullázása
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
        int numberOfVms = 0;
        int filesize = 0;

        try {
            // Legutolsó adatbázisba betöltött adatok lekérése a VM konfigurációkhoz
            RequestData lastUpdateData = requestDataService.getLastData();

            // IaaS környezet inicializálása adott számú fizikai géppel
            IaaSContext context = initializeIaaS(1);

            // Maximális utasítás/másodperc tárolása VM-enként
            Map<String, Long> maxInstrPerSecond = new HashMap<>();

            // VM-ek létrehozása a legutóbbi adatok alapján
            for (VmData vd : lastUpdateData.getVmData()) {
                // Template létrehozása
                VirtualAppliance va = new VirtualAppliance(vd.getName(), vd.getStartupProcess(), vd.getNetworkTraffic(), false, vd.getReqDisk());

                // Erőforrás-korlátozások beállítása
                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());

                // VM regisztrálása és indítása az IaaS-ban
                context.iaas.repositories.get(0).registerObject(va);
                context.iaas.requestVM(va, arc, context.iaas.repositories.get(0), 1);

                // Maximális utasítás/másodperc kiszámítása és tárolása
                long maxInstr = calculateMaxInstructionsPerSecond(vd.getCpu(), vd.getCoreProcessingPower());
                maxInstrPerSecond.put(vd.getName(), maxInstr);
            }

            // Predikciós adatok lekérése az aktuális kérés alapján
            Map<String, List<Double>> predictionData = prediction(currentRequestData);
            /*System.out.println("Pred data:");
            for (Map.Entry<String, List<Double>> entry : predictionData.entrySet()) {
                String key = entry.getKey();
                List<Double> values = entry.getValue();

                System.out.println("Kulcs: " + key);
                for (Double value : values) {
                    System.out.println("  Érték: " + value);
                }
            }*/

            //Percenkénti adatok tárolása
            Map<String, List<Double>> avgLoadsPerMinute = new HashMap<>();
            Map<String, List<Long>> taskInstructionsPerMinute = new HashMap<>();

            // Predikciós adatok feldolgozása percenkénti ablakokban
            for (Map.Entry<String, List<Double>> entry : predictionData.entrySet()) {
                String vmId = entry.getKey();
                List<Double> loadValues = entry.getValue();

                List<Double> avgLoads = new ArrayList<>();
                List<Long> instructions = new ArrayList<>();

                // Adatok csoportosítása percenkénti ablakokba
                for (int i = 0; i < loadValues.size(); i += READINGS_PER_MINUTE) {
                    int endIdx = Math.min(i + READINGS_PER_MINUTE, loadValues.size());
                    List<Double> window = loadValues.subList(i, endIdx);

                    // Átlagos terhelés kiszámítása az ablakra
                    double avg = window.stream().mapToDouble(d -> d).average().orElse(0.0);

                    // Utasítások számának kiszámítása a terhelés alapján
                    long instr = Math.round(SECONDS_PER_MINUTE * (avg / 100.0) * maxInstrPerSecond.get(vmId));

                    avgLoads.add(avg);
                    instructions.add(instr);
                }

                // Feldolgozott adatok tárolása
                avgLoadsPerMinute.put(vmId, avgLoads);
                taskInstructionsPerMinute.put(vmId, instructions);
            }

            // Szimuláció inicializálása, VM-ek elindítása
            Timed.simulateUntilLastEvent();

            // Kezdési időpont rögzítése
            long starttime = Timed.getFireCount();

            // Energia adatgyűjtő beállítása
            setupEDC(context);

            for (PhysicalMachine pm : new ArrayList<>(context.iaas.machines)) {
                for (VirtualMachine vm : new ArrayList<>(pm.listVMs())) {
                    numberOfVms++;

                    // VM azonosító alapján megfelelő terhelési adatok keresése
                    for (Map.Entry<String, List<Double>> entry : avgLoadsPerMinute.entrySet()) {
                        String vmId = entry.getKey();

                        // Ha ez a VM megfelel az aktuális adatsornak
                        if (vm.getVa().id.equals(vmId)) {

                            // Az aktuális VM adatainak lekérése a mapből
                            List<Double> loads = avgLoadsPerMinute.get(vmId);
                            List<Long> tasks = taskInstructionsPerMinute.get(vmId);
                            for (int i = 0; i < loads.size(); i++) {
                                double load = loads.get(i);
                                if (load >= 1) {
                                    load = 1;
                                } else if (load <= 0) {
                                    load = 0;
                                }
                                vm.newComputeTask(tasks.get(i), 1 - load, new DataTransferOnConsumption(context, vm, 600));
                                totalTasks += tasks.get(i);
                            }
                        }
                    }
                }
            }

            // Szimuláció befejezése a teljes predikciós időszakra
            Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * currentRequestData.getPredictionLength()));

            // Energia adatgyűjtő leállítása
            stopEDC();

            // Futásidő statisztikák számítása
            long stoptime = Timed.getFireCount();
            long runtime = stoptime - starttime;

            // Teljesítmény statisztikák generálása
            String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms, null, totalTasks);
            System.out.println("TotalTasks done: " + totalTasks);
            EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);

            // Szimulátor állapot visszaállítása
            Timed.resetTimed();

            return stats;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Predikciós adatok alapján cloud infrastruktúra szimulációt végez.
     * A metódus dinamikus terheléselosztást és backup VM-ek létrehozását kezeli.
     *
     * @param currentRequestData Az aktuális kérési adatok predikciós paraméterekkel
     * @return Szimuláció statisztikáit tartalmazó szöveges összefoglaló
     */
    public String usePredictionWithScaling(RequestData currentRequestData) {
        // Backup virtuális gépek tárolása
        Map<String, VirtualMachine> backUpVms = new ConcurrentHashMap<>();

        // Terhelési küszöbérték - ezen felül backup VM-et hozunk létre
        double loadThreshold = currentRequestData.getThreshold();

        // Globális teljesítménymutatók nullázása
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
        int numberOfVms = 0;
        int filesize = 0;

        try {
            // Legutolsó adatbázisba betöltött adatok lekérése a VM konfigurációkhoz
            RequestData lastUpdateData = requestDataService.getLastData();

            // IaaS környezet inicializálása adott számú fizikai géppel
            IaaSContext context = initializeIaaS(2);

            // Maximális utasítás/másodperc tárolása VM-enként
            Map<String, Long> maxInstrPerSecond = new HashMap<>();

            // VM-ek létrehozása a legutóbbi adatok alapján
            for (VmData vd : lastUpdateData.getVmData()) {
                // Template létrehozása
                VirtualAppliance va = new VirtualAppliance(vd.getName(), vd.getStartupProcess(), vd.getNetworkTraffic(), false, vd.getReqDisk());

                // Erőforrás-korlátozások beállítása
                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());

                // VM regisztrálása és indítása az IaaS-ban
                context.iaas.repositories.get(0).registerObject(va);
                context.iaas.requestVM(va, arc, context.iaas.repositories.get(0), 1);

                // Maximális utasítás/másodperc kiszámítása és tárolása
                long maxInstr = calculateMaxInstructionsPerSecond(vd.getCpu(), vd.getCoreProcessingPower());
                maxInstrPerSecond.put(vd.getName(), maxInstr);
            }

            // Predikciós adatok lekérése az aktuális kérés alapján
            Map<String, List<Double>> predictionData = prediction(currentRequestData);

            //Percenkénti adatok tárolása
            Map<String, List<Double>> avgLoadsPerMinute = new HashMap<>();
            Map<String, List<Long>> taskInstructionsPerMinute = new HashMap<>();

            // Predikciós adatok feldolgozása percenkénti ablakokban
            for (Map.Entry<String, List<Double>> entry : predictionData.entrySet()) {
                String vmId = entry.getKey();
                List<Double> loadValues = entry.getValue();

                List<Double> avgLoads = new ArrayList<>();
                List<Long> instructions = new ArrayList<>();

                // Adatok csoportosítása percenkénti ablakokba
                for (int i = 0; i < loadValues.size(); i += READINGS_PER_MINUTE) {
                    int endIdx = Math.min(i + READINGS_PER_MINUTE, loadValues.size());
                    List<Double> window = loadValues.subList(i, endIdx);

                    // Átlagos terhelés kiszámítása az ablakra
                    double avg = window.stream().mapToDouble(d -> d).average().orElse(0.0);

                    // Utasítások számának kiszámítása a terhelés alapján
                    long instr = Math.round(SECONDS_PER_MINUTE * (avg / 100.0) * maxInstrPerSecond.get(vmId));

                    avgLoads.add(avg);
                    instructions.add(instr);
                }

                // Feldolgozott adatok tárolása
                avgLoadsPerMinute.put(vmId, avgLoads);
                taskInstructionsPerMinute.put(vmId, instructions);
            }

            // Szimuláció inicializálása, VM-ek elindítása
            Timed.simulateUntilLastEvent();

            // Kezdési időpont rögzítése
            long starttime = Timed.getFireCount();

            // Energia adatgyűjtő beállítása
            setupEDC(context);

            // Backup VM-ek létrehozási idejének követése
            int lastBackupCreationMinute = 0;

            List<PhysicalMachine> pms = context.iaas.machines;

            for (PhysicalMachine pm : pms) {
                for (VirtualMachine vm : new ArrayList<>(pm.listVMs())) {
                    numberOfVms++;

                    // VM azonosító alapján megfelelő terhelési adatok keresése
                    for (Map.Entry<String, List<Double>> entry : avgLoadsPerMinute.entrySet()) {
                        String vmId = entry.getKey();

                        // Ha ez a VM megfelel az aktuális adatsornak
                        if (vm.getVa().id.equals(vmId)) {

                            // Az aktuális VM adatainak lekérése a mapből
                            List<Double> loads = avgLoadsPerMinute.get(vmId);
                            List<Long> tasks = taskInstructionsPerMinute.get(vmId);
                            for (int i = 0; i < loads.size(); i++) {

                                // Magas terhelés esetén (pl. >= 80%) backup VM használata
                                if (loads.get(i) >= 0.8) {

                                    // Ha már létezik backup VM ehhez a VM-hez
                                    if (backUpVms.containsKey(vmId)) {
                                        filesize = 600;
                                        // Feladat szétosztása az eredeti és backup VM között (50-50%)
                                        vm.newComputeTask((double) tasks.get(i) / 2, 1 - (loads.get(i) / 2), new DataTransferOnConsumption(context, vm, filesize));
                                        totalTasks += (int) (tasks.get(i) / 2);
                                        backUpVms.get(vmId).newComputeTask((double) tasks.get(i) / 2, 1 - (loads.get(i) / 2), new DataTransferOnConsumption(context, backUpVms.get(vmId), filesize));
                                        totalTasks += (int) (tasks.get(i) / 2);
                                    } else {
                                        // Új backup VM létrehozása
                                        for (VmData vd : lastUpdateData.getVmData()) {
                                            if (vd.getName().equals(vmId)) {
                                                // Backup VM template létrehozása
                                                VirtualAppliance va = new VirtualAppliance(vd.getName() + "backup", 0, vd.getNetworkTraffic(), false, vd.getReqDisk());

                                                // Backup VM erőforrás-korlátozások
                                                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());

                                                // Backup VM regisztrálása és indítása
                                                synchronized(context.iaas.repositories.get(0)) {
                                                    context.iaas.repositories.get(0).registerObject(va);
                                                    VirtualMachine backUp = context.iaas.requestVM(va, arc, context.iaas.repositories.get(0), 1)[0];
                                                    backUpVms.put(vmId, backUp);
                                                }
                                                numberOfVms++;

                                                //Backup VM indításának időpontjának rögzítése
                                                lastBackupCreationMinute = i + 1;

                                                // Szimuláció indítása a backup VM indulásáig
                                                Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * lastBackupCreationMinute));

                                                // Feladatok szétosztása az eredeti és backup VM között
                                                vm.newComputeTask((double) tasks.get(i) / 2, 1 - (loads.get(i) / 2), new DataTransferOnConsumption(context, vm, 600));
                                                totalTasks += (int) (tasks.get(i) / 2);
                                                backUpVms.get(vmId).newComputeTask((double) tasks.get(i) / 2, 1 - (loads.get(i) / 2), new DataTransferOnConsumption(context, backUpVms.get(vmId), 600));
                                                totalTasks += (int) (tasks.get(i) / 2);
                                            }
                                        }
                                    }
                                } else {
                                    // Normál terhelés esetén (<80%) csak az eredeti VM használata
                                    vm.newComputeTask(tasks.get(i), 1 - loads.get(i), new DataTransferOnConsumption(context, vm, 600));
                                    totalTasks += (tasks.get(i));
                                }
                            }
                        }
                    }
                }
            }

            // Szimuláció befejezése a teljes predikciós időszakra
            Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * (currentRequestData.getPredictionLength() - lastBackupCreationMinute)));

            // Energia adatgyűjtő leállítása
            stopEDC();

            // Futásidő statisztikák számítása
            long stoptime = Timed.getFireCount();
            long runtime = stoptime - starttime;

            // Teljesítmény statisztikák generálása
            String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms, backUpVms, totalTasks);
            System.out.println("TotalTasks done: " + totalTasks);
            EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);

            // Szimulátor állapot visszaállítása
            Timed.resetTimed();

            if (!backUpVms.isEmpty()) {
                backUpVms.clear();
            }

            return stats;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Alapvető (baseline) szimulációt végez konstans terhelési értékekkel.
     * Nem használ predikciót, hanem a jelenlegi VM használati értékeket alkalmazza végig.
     *
     * @param currentRequestData A szimuláció paraméterei (időtartam, VM-ek száma)
     * @return JSON formátumú statisztikai összefoglaló a szimuláció eredményeiről
     */
    public String doBaseline(RequestData currentRequestData) {
        // Teljesítménymutatók nullázása az új szimuláció előtt
        totalEnergyConsumption = 0.0;
        totalMovedData = 0;
        totalTasks = 0;
        int numberOfVms = 0;

        try {
            // IaaS környezet inicializálása adott számú fizikai géppel
            IaaSContext context = initializeIaaS(1);

            // Legutóbbi VM konfigurációs adatok lekérése
            RequestData lastUpdateData = requestDataService.getLastData();


            // Maximális utasítás/másodperc értékek tárolása VM-enként
            Map<String, Long> maxInstrPerSecond = new HashMap<>();

            // VM-ek létrehozása a legutóbbi adatok alapján
            for (VmData vd : lastUpdateData.getVmData()) {
                VirtualAppliance va = new VirtualAppliance(vd.getName(), vd.getStartupProcess(), vd.getNetworkTraffic(), false, vd.getReqDisk());
                AlterableResourceConstraints arc = new AlterableResourceConstraints(vd.getCpu(), vd.getCoreProcessingPower(), vd.getRam());
                context.iaas.repositories.get(0).registerObject(va);
                context.iaas.requestVM(va, arc, context.iaas.repositories.get(0), 1);
                long maxInstr = calculateMaxInstructionsPerSecond(vd.getCpu(), vd.getCoreProcessingPower());
                maxInstrPerSecond.put(vd.getName(), maxInstr);
            }

            // Szimuláció futtatása, VM-ek elindítása
            Timed.simulateUntilLastEvent();
            long starttime = Timed.getFireCount();

            // Energia adatgyűjtő beállítása
            setupEDC(context);

            for (PhysicalMachine pm : context.iaas.machines) {
                for (VirtualMachine vm : pm.listVMs()) {
                    numberOfVms++;
                    for (VmData vd : lastUpdateData.getVmData()) {
                        if (vd.getName().equals(vm.getVa().id)) {
                            // Konstans terhelés alkalmazása a teljes szimuláció időtartamára
                            for (int i = 0; i < currentRequestData.getPredictionLength(); i++) {
                                // Utasítások számának kiszámítása a jelenlegi használat alapján
                                long instr = Math.round(SECONDS_PER_MINUTE *
                                            (vd.getUsage() / 100.0) *
                                            maxInstrPerSecond.get(vd.getName()));
                                totalTasks += (int) instr;
                                // Megfelelő számú task hozzáadása a VM-hez
                                vm.newComputeTask(instr,
                                        1 - vd.getUsage(),
                                        new DataTransferOnConsumption(context, vm, 600));
                            }
                        }
                    }
                }
            }

            // Szimuláció futtatása a teljes predikciós időszakra
            Timed.simulateUntil(Timed.getFireCount() + (60L * 1000 * currentRequestData.getPredictionLength()));

            // Energia adatgyűjtő leállítása
            stopEDC();

            // Futásidő statisztikák számítása
            long stoptime = Timed.getFireCount();
            long runtime = stoptime - starttime;
            String stats = generateRuntimeStats(runtime, lastUpdateData, totalEnergyConsumption, totalMovedData, numberOfVms, null, totalTasks);
            System.out.println("TotalTasks done: " + totalTasks);
            EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);

            // Szimulátor állapot visszaállítása
            Timed.resetTimed();

            return stats;


        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException |
                 VMManager.VMManagementException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Szimuláció futásidejére vonatkozó statisztikákat generál JSON formátumban.
     *
     * @param runtime Futásidő milliszekundumban
     * @param lastUpdateData VM konfigurációs adatok
     * @param totalEnergyConsumption Összesített energiafogyasztás kWh-ban
     * @param totalMovedData Összesített adatátvitel MB-ban
     * @param vms Használt VM-ek száma
     * @param backUpVms Backup VM-ek térképe (lehet null baseline esetén)
     * @return JSON formátumú statisztikai összefoglaló
     */
    public String generateRuntimeStats(long runtime, RequestData lastUpdateData,
                                       double totalEnergyConsumption,
                                       int totalMovedData,
                                       int vms, Map<String, VirtualMachine> backUpVms,
                                       int totalTasks) {
        double hours = runtime / 3600000.0;
        double minutes = runtime / 60000.0;

        // IoT költségek kiszámítása
        double iotCost = calculateIoTCost(lastUpdateData, hours, backUpVms);

        try {
            // JSON objektum mapper inicializálása formázott kimenettel
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            // Statisztikai adatok strukturált tárolása
            Map<String, Object> statsMap = new LinkedHashMap<>();
            statsMap.put("runtime_ms", runtime);
            statsMap.put("runtime_minutes", minutes);
            statsMap.put("runtime_hours", hours);
            statsMap.put("total_iot_cost_usd", iotCost);
            statsMap.put("total_energy_consumption_kwh", totalEnergyConsumption);
            statsMap.put("total_moved_data_mb", totalMovedData);
            statsMap.put("total_vm_tasks_simulated", totalTasks);
            statsMap.put("number_of_vms_utilized", vms);

            return objectMapper.writeValueAsString(statsMap);
        } catch (JsonProcessingException e) {
            return "Failed to generate JSON stats";
        }
    }

    /**
     * IoT infrastruktúra költségeit számítja ki a VM-ek erőforrás-felhasználása alapján.
     *
     * @param rd VM adatok
     * @param time Futásidő órában
     * @param backUpVms Backup VM-ek térképe (lehet null)
     * @return Összesített költség USD-ben
     */
    public double calculateIoTCost(RequestData rd, double time, Map<String, VirtualMachine> backUpVms) {
        double ramCost = 0.005;
        double cpuCost = 0.05;
        double totalCost = 0.0;

        // Minden VM költségének kiszámítása
        for (VmData vd : rd.getVmData()) {
            // RAM költség: byte -> GB konverzió és költségszámítás
            totalCost += (vd.getRam() / (1024.0 * 1024.0 * 1024.0)) * ramCost * time;

            // CPU költség: magok száma * költség * idő
            totalCost += vd.getCpu() * cpuCost * time;

            // Backup VM-ek költségének hozzáadása (ha vannak)
            if (backUpVms != null) {
                if (backUpVms.containsKey(vd.getName())) {
                    totalCost += (vd.getRam() / (1024.0 * 1024.0 * 1024.0)) * ramCost * time;
                    totalCost += vd.getCpu() * cpuCost * time;
                }
            }
        }
        return totalCost;
    }

    /**
     * Maximális utasítás/másodperc érték kiszámítása VM erőforrások alapján.
     *
     * @param numCores CPU magok száma
     * @param instrPerMsPerCore Utasítás/milliszekundum per mag
     * @return Maximális utasítás/másodperc érték
     */
    private static long calculateMaxInstructionsPerSecond(int numCores, double instrPerMsPerCore) {
        // Konverzió: magok * utasítás/ms/mag * 1000 (ms -> sec)
        return (long) (numCores * instrPerMsPerCore * 1000); // ms -> sec
    }

    /**
     * Python alapú gépi tanulási modell meghívása terhelés-előrejelzéshez.
     * Lineáris regressziós modellt használ a VM terhelések predikciójára.
     *
     * @param requestData Predikciós paraméterek
     * @return VM-enkénti terhelési előrejelzések
     */
    public Map<String, List<Double>> prediction(RequestData requestData) {
        try {
            String scriptPath = "src/main/resources/scripts/prediction.py";
            // Python folyamat konfigurálása paraméterekkel
            ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, requestData.getFeatureName(), Integer.toString(requestData.getBasedOnLast() * 12), Integer.toString(requestData.getPredictionLength() * 60), Integer.toString(requestData.getVmsCount()), TenantContext.getTenantId());
            processBuilder.redirectErrorStream(true);

            // Python szkript indítása
            Process process = processBuilder.start();

            // Python kimenet olvasása
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder jsonData = new StringBuilder();
            boolean jsonStarted = false;
            boolean jsonEnded = false;
            Map<String, List<Double>> predictionData = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                if (line.equals("JSON_DATA_START")) {
                    jsonStarted = true;
                    continue;
                }
                if (line.equals("JSON_DATA_END")) {
                    jsonEnded = true;
                    continue;
                }

                if (jsonStarted && !jsonEnded) {
                    jsonData.append(line);
                } else {
                    System.out.println(line);
                }
            }

            if (!jsonData.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                predictionData = objectMapper.readValue(
                        jsonData.toString(),
                        new TypeReference<Map<String, List<Double>>>() {
                        }
                );
            }

            int exitCode = process.waitFor();
            System.out.println("Python script exited with code: " + exitCode);
            return predictionData;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}


