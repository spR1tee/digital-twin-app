package hu.digital_twin.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

// Segédosztály, amely inicializálja az adatbázis sémát egy SQL fájl alapján
public class SchemaInitializer {

    // Statikus metódus, amely lefuttatja a "schema.sql" szkriptet a megadott DataSource-on
    public static void initializeSchema(DataSource dataSource) {
        // Létrehoz egy SQL szkript futtatót, amely képes osztályútvonalról betölteni erőforrásokat
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();

        // Hozzáadja a 'schema.sql' fájlt, amelynek az adatbázis sémát kell definiálnia (CREATE TABLE stb.)
        populator.addScript(new ClassPathResource("schema.sql"));

        // Lefuttatja a szkriptet a megadott DataSource-on
        populator.execute(dataSource);
    }
}
