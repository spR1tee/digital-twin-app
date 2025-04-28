package hu.digital_twin.service;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

public class Transfer extends ConsumptionEventAdapter {

    Repository from;
    Repository to;
    StorageObject so;
    long start;

    public Transfer(Repository from, Repository to, StorageObject so) throws NetworkNode.NetworkException {
        this.from = from;
        this.to = to;
        this.so = so;
        this.from.registerObject(so);
        this.from.requestContentDelivery(so.id, to, this);
        this.start = Timed.getFireCount();
    }

    @Override
    public void conComplete() {
        this.from.deregisterObject(this.so);
        System.out.println("Start: " + this.start + " from: " +
                this.from.getName() + " to: " + this.to.getName() + " end: " + Timed.getFireCount());
    }


}