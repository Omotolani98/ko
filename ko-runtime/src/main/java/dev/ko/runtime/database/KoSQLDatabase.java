package dev.ko.runtime.database;

/**
 * Developer-facing database API. In Phase 2 MVP this is a stub;
 * actual JDBC operations will be added when ko-cli provisions
 * Testcontainers databases.
 */
public class KoSQLDatabase {

    private final String name;

    public KoSQLDatabase(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
