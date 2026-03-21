package dev.ko.test;

import dev.ko.runtime.database.KoSQLDatabase;

import java.util.List;
import java.util.Map;

/**
 * Test helper for inspecting and manipulating database state in tests.
 *
 * <pre>{@code
 * @Autowired TestDatabase testDatabase;
 *
 * @Test
 * void createGreeting_insertsRow() {
 *     // ... trigger API call ...
 *     var rows = testDatabase.query("greetings", "SELECT * FROM greetings");
 *     assertThat(rows).hasSize(1);
 *     assertThat(rows.get(0).get("name")).isEqualTo("world");
 * }
 *
 * @BeforeEach
 * void setUp() {
 *     testDatabase.truncate("greetings", "greetings");
 * }
 * }</pre>
 */
public class TestDatabase {

    private final Map<String, KoSQLDatabase> databases;

    /**
     * Creates a new TestDatabase wrapping the given database map.
     *
     * @param databases map of database name to {@link KoSQLDatabase} instance
     */
    public TestDatabase(Map<String, KoSQLDatabase> databases) {
        this.databases = databases;
    }

    /**
     * Get a named database.
     */
    public KoSQLDatabase db(String name) {
        KoSQLDatabase db = databases.get(name);
        if (db == null) {
            throw new IllegalArgumentException("No database named '" + name
                    + "'. Available: " + databases.keySet());
        }
        return db;
    }

    /**
     * Execute a query on a named database.
     */
    public List<Map<String, Object>> query(String databaseName, String sql, Object... params) {
        return db(databaseName).query(sql, params);
    }

    /**
     * Execute a single-row query on a named database.
     */
    public Map<String, Object> queryRow(String databaseName, String sql, Object... params) {
        return db(databaseName).queryRow(sql, params);
    }

    /**
     * Execute a statement on a named database.
     */
    public int exec(String databaseName, String sql, Object... params) {
        return db(databaseName).exec(sql, params);
    }

    /**
     * Truncate a table in a named database. Useful in {@code @BeforeEach} for test isolation.
     */
    public void truncate(String databaseName, String tableName) {
        db(databaseName).exec("DELETE FROM " + tableName);
    }

    /**
     * Count rows in a table.
     */
    public long count(String databaseName, String tableName) {
        Map<String, Object> row = db(databaseName).queryRow("SELECT COUNT(*) AS cnt FROM " + tableName);
        if (row == null) return 0;
        return ((Number) row.get("cnt")).longValue();
    }

    /**
     * Returns all available database names.
     */
    public java.util.Set<String> databaseNames() {
        return databases.keySet();
    }
}
