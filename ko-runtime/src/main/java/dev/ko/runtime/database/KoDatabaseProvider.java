package dev.ko.runtime.database;

import javax.sql.DataSource;

/**
 * Provider interface for database infrastructure.
 * Implementations supply DataSources for each named database.
 */
public interface KoDatabaseProvider {

    /**
     * Create or return a DataSource for the given database name.
     */
    DataSource getDataSource(String databaseName);

    /**
     * Shut down all managed DataSources.
     */
    void close();
}
