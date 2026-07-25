package com.asosiaciondeasis.animalesdeasis.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Provides the shared SQLite connection for the application.
 *
 * <p>Every connection is opened with a set of PRAGMAs that are <b>per-connection</b>
 * in SQLite and therefore easy to forget:</p>
 * <ul>
 *   <li>{@code foreign_keys = ON} — without this SQLite silently ignores the
 *       {@code ON DELETE CASCADE}/{@code SET NULL} clauses declared in the schema,
 *       so deleting an animal would orphan its vaccines.</li>
 *   <li>{@code journal_mode = WAL} — lets the background sync thread read while the
 *       UI writes (and vice-versa) instead of blocking on a single global lock.</li>
 *   <li>{@code busy_timeout = 5000} — waits up to 5s for a lock instead of failing
 *       immediately with "database is locked".</li>
 * </ul>
 */
public class DatabaseConnection {

    private static final String DB_PATH = System.getProperty("user.home") + "/.asociaciondeasis/AsociacionDeAsis.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;
    private static Connection connection;

    private DatabaseConnection() {
        // Private constructor to prevent instantiation
    }

    /**
     * Returns the shared SQLite connection, (re)opening it if needed and applying
     * the required PRAGMAs.
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            applyPragmas(connection);
        }
        return connection;
    }

    /** Applies the per-connection PRAGMAs described in the class Javadoc. */
    public static void applyPragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA busy_timeout = 5000");
        }
    }
}
