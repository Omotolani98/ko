package dev.ko.runtime.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local development database provider using H2 in-memory databases.
 * Each named database gets its own isolated H2 instance.
 */
public class LocalDatabaseProvider implements KoDatabaseProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalDatabaseProvider.class);

    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    @Override
    public DataSource getDataSource(String databaseName) {
        return dataSources.computeIfAbsent(databaseName, name -> {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
            ds.setUsername("sa");
            ds.setPassword("");
            log.info("Ko: Created local H2 database '{}'", name);
            return ds;
        });
    }

    @Override
    public void close() {
        dataSources.clear();
    }
}
