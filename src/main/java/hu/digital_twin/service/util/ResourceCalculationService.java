package hu.digital_twin.service.util;

import hu.digital_twin.model.RequestData;
import hu.digital_twin.model.VmData;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ResourceCalculationService {

    private static final double RAM_COST_PER_GB_PER_HOUR = 0.005;
    private static final double CPU_COST_PER_CORE_PER_HOUR = 0.05;

    /**
     * IoT infrastruktúra költségeit számítja ki a VM-ek erőforrás-felhasználása alapján.
     *
     * @param rd VM adatok
     * @param time Futásidő órában
     * @param backUpVms Backup VM-ek térképe (lehet null)
     * @return Összesített költség USD-ben
     */
    public double calculateIoTCost(RequestData rd, double time, Map<String, VirtualMachine> backUpVms) {
        double totalCost = 0.0;

        for (VmData vd : rd.getVmData()) {
            totalCost += (vd.getRam() / (1024.0 * 1024.0 * 1024.0)) * RAM_COST_PER_GB_PER_HOUR * time;
            totalCost += vd.getCpu() * CPU_COST_PER_CORE_PER_HOUR * time;

            if (backUpVms != null && backUpVms.containsKey(vd.getName())) {
                totalCost += (vd.getRam() / (1024.0 * 1024.0 * 1024.0)) * RAM_COST_PER_GB_PER_HOUR * time;
                totalCost += vd.getCpu() * CPU_COST_PER_CORE_PER_HOUR * time;
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
    public long calculateMaxInstructionsPerSecond(int numCores, double instrPerMsPerCore) {
        return (long) (numCores * instrPerMsPerCore * 1000); // ms -> sec
    }
}

