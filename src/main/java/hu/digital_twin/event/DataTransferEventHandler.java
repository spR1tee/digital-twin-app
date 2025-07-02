package hu.digital_twin.event;

import hu.digital_twin.service.util.TransferHelperService;
import hu.digital_twin.context.IaaSContext;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;

public class DataTransferEventHandler extends ConsumptionEventAdapter {

    private final VirtualMachine vm;
    private final IaaSContext context;
    private final int fileSize;
    private final Runnable onDataTransferred;

    public DataTransferEventHandler(IaaSContext context, VirtualMachine vm, int fileSize, Runnable onDataTransferred) {
        this.context = context;
        this.vm = vm;
        this.fileSize = fileSize;
        this.onDataTransferred = onDataTransferred;
    }

    @Override
    public void conComplete() {
        Repository source = null;
        Repository target = null;

        for (int i = 0; i < context.pms.size(); i++) {
            if (context.pms.get(i).listVMs().contains(vm)) {
                source = context.pmRepos.get(i);
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
            new TransferHelperService(source, target, new StorageObject("data", fileSize, false));
            onDataTransferred.run();
        } catch (NetworkNode.NetworkException e) {
            throw new RuntimeException("Transfer failed", e);
        }
    }
}

