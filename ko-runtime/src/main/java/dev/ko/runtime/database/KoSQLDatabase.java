package dev.ko.runtime.database;

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

    public KoSQLDatabase(String name, DataSource dataSource) {
        this.name = name;
        this.dataSource = dataSource;
    }

    public String getName() {
        return name;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Execute a query and return all rows as a list of maps.
     */
    public List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        } catch (SQLException e) {
            throw new KoDatabaseException("Query failed on database '" + name + "': " + sql, e);
        }
    }

    /**
     * Execute a query and return the first row, or null if no results.
     */
    public Map<String, Object> queryRow(String sql, Object... params) {
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
            throw new KoDatabaseException("Query failed on database '" + name + "': " + sql, e);
        }
    }

    /**
     * Execute an INSERT, UPDATE, or DELETE statement.
     * Returns the number of affected rows.
     */
    public int exec(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new KoDatabaseException("Exec failed on database '" + name + "': " + sql, e);
        }
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

    @FunctionalInterface
    public interface TxAction {
        void execute(TxContext tx) throws Exception;
    }

    /**
     * Transaction context providing query/exec within a single connection.
     */
    public static class TxContext {
        private final Connection conn;

        TxContext(Connection conn) {
            this.conn = conn;
        }

        public List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapRows(rs);
                }
            }
        }

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
