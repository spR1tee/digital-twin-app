package hu.digital_twin.config;

import hu.digital_twin.context.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

// Egyedi RoutingDataSource implementáció, amely tenant ID alapján vált adatforrást
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    // Ezt a metódust hívja meg a Spring minden adatbázis lekérésnél
    @Override
    protected Object determineCurrentLookupKey() {
        // Visszaadja az aktuális tenant ID-t, amit a TenantContext tárol (ThreadLocal)
        // Ez alapján választja ki a megfelelő DataSource-t a targetDataSources map-ből
        return TenantContext.getTenantId();
    }
}
