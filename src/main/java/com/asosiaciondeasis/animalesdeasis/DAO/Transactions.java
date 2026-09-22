package com.asosiaciondeasis.animalesdeasis.DAO;

import javax.sql.DataSource;
import java.sql.Connection;

/** Runs a unit of work on its own connection, committing it whole or not at all. */
public final class Transactions {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection conn) throws Exception;
    }

    private Transactions() {
    }

    /**
     * The connection is private to this call, so the transaction cannot sweep in
     * statements from other threads - which is what happened when every thread
     * shared one connection.
     */
    public static <T> T inTransaction(DataSource dataSource, Work<T> work) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                // sqlite-jdbc begins a new transaction straight after commit or
                // rollback while auto-commit is off, and transactions here take the
                // write lock immediately. Restoring auto-commit releases it now
                // rather than whenever the connection happens to close.
                conn.setAutoCommit(true);
            }
        }
    }
}
