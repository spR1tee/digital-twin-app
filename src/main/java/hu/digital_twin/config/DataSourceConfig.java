package hu.digital_twin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class DataSourceConfig {
    private final Map<Object, Object> dataSources = new ConcurrentHashMap<>();

    @Bean
    public DataSource dataSource() {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        routingDataSource.setTargetDataSources(dataSources);
        routingDataSource.setDefaultTargetDataSource(createAndRegister("default"));
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    public synchronized DataSource createAndRegister(String tenantId) {
        if (!dataSources.containsKey(tenantId)) {
            String url = "jdbc:sqlite:src/main/resources/spring_db/" + tenantId + ".db";
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.sqlite.JDBC");
            ds.setUrl(url);
            SchemaInitializer.initializeSchema(ds);
            dataSources.put(tenantId, ds);
            ((TenantRoutingDataSource) dataSource()).setTargetDataSources(dataSources);
            ((TenantRoutingDataSource) dataSource()).afterPropertiesSet();
        }
        return (DataSource) dataSources.get(tenantId);
    }
}
