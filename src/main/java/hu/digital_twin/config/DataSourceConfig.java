package hu.digital_twin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Konfigurációs osztály az adatforrások kezeléséhez több-bérlős környezetben
@Configuration
public class DataSourceConfig {
    private final Map<Object, Object> dataSources = new ConcurrentHashMap<>();

    // Spring Bean létrehozása az alapértelmezett DataSource-hoz (ez lesz a routing datasource)
    @Bean
    public DataSource dataSource() {
        // Létrehoz egy speciális DataSource-t, amely képes bérlő szerint váltani (routing)
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        // Beállítja az összes elérhető tenant adatforrást (jelenlegi üres vagy bővülő)
        routingDataSource.setTargetDataSources(dataSources);

        // Beállítja az alapértelmezett adatforrást (ha nincs tenant kiválasztva)
        routingDataSource.setDefaultTargetDataSource(createAndRegister("default"));

        // Frissíti a routing datasource belső állapotát
        routingDataSource.afterPropertiesSet();

        // Visszatér a konfigurált routing datasource-szal
        return routingDataSource;
    }

    // Tenant-hez tartozó adatbázis létrehozása és regisztrálása, ha még nem létezik
    public synchronized DataSource createAndRegister(String tenantId) {
        // Csak akkor hoz létre új DataSource-t, ha még nincs ilyen tenant regisztrálva
        if (!dataSources.containsKey(tenantId)) {
            // SQLite adatbázis elérési útjának összeállítása tenant ID alapján
            String url = "jdbc:sqlite:src/main/resources/spring_db/" + tenantId + ".db";

            // Létrehoz egy új SQLite DataSource-t
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.sqlite.JDBC");
            ds.setUrl(url);

            // Inicializálja az adatbázis sémát (táblák, stb.)
            SchemaInitializer.initializeSchema(ds);

            // Hozzáadja az új DataSource-t a tárolóhoz
            dataSources.put(tenantId, ds);

            // Frissíti a TenantRoutingDataSource adatforrásait a bővített Map-el
            ((TenantRoutingDataSource) dataSource()).setTargetDataSources(dataSources);

            // Újrainicializálja a routing datasource-t, hogy felismerje az új tenantot
            ((TenantRoutingDataSource) dataSource()).afterPropertiesSet();
        }
        // Visszatér a meglévő vagy frissen létrehozott tenant DataSource-szal
        return (DataSource) dataSources.get(tenantId);
    }
}
