package dev.ko.runtime.database;

import dev.ko.runtime.tracing.KoSpan;
import dev.ko.runtime.tracing.KoSpanCollector;
import dev.ko.runtime.tracing.TracingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Developer-facing SQL database API.
 * Each @KoDatabase field gets an instance backed by its own DataSource.
 *
 * <pre>
 * {@code
 * @KoDatabase(name = "users")
 * private KoSQLDatabase db;
 *
 * var user = db.queryRow("SELECT * FROM users WHERE id = ?", Map.class, id);
 * var users = db.query("SELECT * FROM users WHERE active = ?", Map.class, true);
 * db.exec("INSERT INTO users (name, email) VALUES (?, ?)", name, email);
 * }
 * </pre>
 */
public class KoSQLDatabase {

    private static final Logger log = LoggerFactory.getLogger(KoSQLDatabase.class);

    private final String name;
    private final DataSource dataSource;
    private volatile KoSpanCollector spanCollector;

    /**
     * Creates a new KoSQLDatabase.
     *
     * @param name the logical database name
     * @param dataSource the JDBC data source
     */
    public KoSQLDatabase(String name, DataSource dataSource) {
        this.name = name;
        this.dataSource = dataSource;
    }

    /** Set the span collector for tracing. Called by auto-configuration. */
    public void setSpanCollector(KoSpanCollector collector) {
        this.spanCollector = collector;
    }

    /**
     * Returns the logical database name.
     *
     * @return the database name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the underlying JDBC data source.
     *
     * @return the data source
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Execute a query and return all rows as a list of maps.
     */
    public List<Map<String, Object>> query(String sql, Object... params) {
        TracingContext ctx = traceStart();
        long start = System.currentTimeMillis();
        String status = "OK";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        } catch (SQLException e) {
            status = "ERROR";
            throw new KoDatabaseException("Query failed on database '" + name + "': " + sql, e);
        } finally {
            traceEnd(ctx, "db.query " + name, sql, start, status);
        }
    }

    /**
     * Execute a query and return the first row, or null if no results.
     */
    public Map<String, Object> queryRow(String sql, Object... params) {
        TracingContext ctx = traceStart();
        long start = System.currentTimeMillis();
        String status = "OK";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            status = "ERROR";
            throw new KoDatabaseException("Query failed on database '" + name + "': " + sql, e);
        } finally {
            traceEnd(ctx, "db.queryRow " + name, sql, start, status);
        }
    }

    /**
     * Execute an INSERT, UPDATE, or DELETE statement.
     * Returns the number of affected rows.
     */
    public int exec(String sql, Object... params) {
        TracingContext ctx = traceStart();
        long start = System.currentTimeMillis();
        String status = "OK";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            status = "ERROR";
            throw new KoDatabaseException("Exec failed on database '" + name + "': " + sql, e);
        } finally {
            traceEnd(ctx, "db.exec " + name, sql, start, status);
        }
    }

    private TracingContext traceStart() {
        if (spanCollector == null || TracingContext.current() == null) return null;
        return TracingContext.childSpan();
    }

    private void traceEnd(TracingContext ctx, String operation, String sql, long start, String status) {
        if (ctx == null || spanCollector == null) return;
        long duration = System.currentTimeMillis() - start;
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("db.name", name);
        attrs.put("db.sql", sql.length() > 500 ? sql.substring(0, 500) : sql);
        spanCollector.submit(new KoSpan(
                ctx.traceId(), ctx.spanId(), ctx.parentSpanId(),
                name, operation, "DATABASE",
                start, duration, status, attrs
        ));
        ctx.restore();
    }

    /**
     * Execute a batch of statements within a transaction.
     */
    public void tx(TxAction action) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                action.execute(new TxContext(conn));
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (KoDatabaseException e) {
            throw e;
        } catch (Exception e) {
            throw new KoDatabaseException("Transaction failed on database '" + name + "'", e);
        }
    }

    /**
     * Functional interface for transactional operations.
     */
    @FunctionalInterface
    public interface TxAction {
        /**
         * Executes operations within a transaction.
         *
         * @param tx the transaction context
         * @throws Exception if the transaction should be rolled back
         */
        void execute(TxContext tx) throws Exception;
    }

    /**
     * Transaction context providing query/exec within a single connection.
     */
    public static class TxContext {
        private final Connection conn;

        /**
         * Creates a transaction context wrapping the given connection.
         *
         * @param conn the JDBC connection
         */
        TxContext(Connection conn) {
            this.conn = conn;
        }

        /**
         * Execute a query within this transaction and return all rows.
         *
         * @param sql the SQL query
         * @param params the query parameters
         * @return list of row maps
         * @throws SQLException if the query fails
         */
        public List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapRows(rs);
                }
            }
        }

        /**
         * Execute a query within this transaction and return the first row, or null.
         *
         * @param sql the SQL query
         * @param params the query parameters
         * @return the first row as a map, or {@code null}
         * @throws SQLException if the query fails
         */
        public Map<String, Object> queryRow(String sql, Object... params) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    return null;
                }
            }
        }

        /**
         * Execute an INSERT, UPDATE, or DELETE within this transaction.
         *
         * @param sql the SQL statement
         * @param params the statement parameters
         * @return the number of affected rows
         * @throws SQLException if the statement fails
         */
        public int exec(String sql, Object... params) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParams(ps, params);
                return ps.executeUpdate();
            }
        }
    }

    private static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private static List<Map<String, Object>> mapRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(mapRow(rs));
        }
        return rows;
    }

    private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(cols);
        for (int i = 1; i <= cols; i++) {
            row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
        }
        return row;
    }
}
