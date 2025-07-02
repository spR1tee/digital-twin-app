package hu.digital_twin.service.infrastructure;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NetworkConfigurationService {
    private static final int DEFAULT_LATENCY_MS = 100;

    /**
     * Hálózati késleltetések beállítása repository-k között
     */
    public void configureLatencies(Repository source, Repository target, int latencyMs) {
        if (!source.getName().equals(target.getName())) {
            source.addLatencies(target.getName(), latencyMs);
            target.addLatencies(source.getName(), latencyMs);
        }
    }

    /**
     * Alapértelmezett késleltetések beállítása
     */
    public void configureDefaultLatencies(Repository source, Repository target) {
        configureLatencies(source, target, DEFAULT_LATENCY_MS);
    }

    /**
     * Új repository hálózati integrációja a meglévő repository-kkal
     */
    public void integrateRepositoryToNetwork(Repository newRepo, Repository cloudRepo, List<Repository> existingRepos) {
        // Cloud repo-val való kapcsolat beállítása
        if (cloudRepo != null) {
            cloudRepo.addLatencies(newRepo.getName(), DEFAULT_LATENCY_MS);
            newRepo.addLatencies(cloudRepo.getName(), DEFAULT_LATENCY_MS);
        }

        // Összes többi repository-val való kapcsolat beállítása
        for (Repository repo : existingRepos) {
            configureDefaultLatencies(newRepo, repo);
        }
    }
}
