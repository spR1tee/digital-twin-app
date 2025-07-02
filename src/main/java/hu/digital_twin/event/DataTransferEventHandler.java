package hu.digital_twin.event;

import hu.digital_twin.service.util.TransferHelperService;
import hu.digital_twin.context.IaaSContext;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;

// Adatátviteli eseménykezelő, mely a ConsumptionEventAdapter-től öröklődik
public class DataTransferEventHandler extends ConsumptionEventAdapter {

    private final VirtualMachine vm;              // Az érintett virtuális gép
    private final IaaSContext context;             // Szimulációs IaaS kontextus (tárolja a fizikai gépeket, repository-kat stb.)
    private final int fileSize;                     // Átviteli méret bájtban vagy más egységben
    private final Runnable onDataTransferred;      // Callback, amit az adatátvitel befejezése után futtatunk

    // Konstruktor: beállítja az összes szükséges mezőt
    public DataTransferEventHandler(IaaSContext context, VirtualMachine vm, int fileSize, Runnable onDataTransferred) {
        this.context = context;
        this.vm = vm;
        this.fileSize = fileSize;
        this.onDataTransferred = onDataTransferred;
    }

    // Amikor a taskok szimulációja befejeződik, ezt a metódust hívja a szimuláció
    @Override
    public void conComplete() {
        Repository source = null;
        Repository target = null;

        // Megkeresi a forrás repository-t, amihez az adott VM tartozik (fizikai gép index alapján)
        for (int i = 0; i < context.pms.size(); i++) {
            if (context.pms.get(i).listVMs().contains(vm)) {
                source = context.pmRepos.get(i);

                // Kiválaszt egy másik repository-t, ami célként szolgál (nem azonos a forrás repository-val)
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
            // Létrehoz egy TransferHelperService-t az adat átvitelére a source és target repository között
            // A StorageObject a továbbítandó adatot reprezentálja
            new TransferHelperService(source, target, new StorageObject("data", fileSize, false));

            // Ha sikeres az adatátvitel, futtatja a callback-et: metrikák frissítése
            onDataTransferred.run();
        } catch (NetworkNode.NetworkException e) {
            throw new RuntimeException("Transfer failed", e);
        }
    }
}
