package com.asosiaciondeasis.animalesdeasis.Config;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * Where the SQLite database lives, and how connections to it are opened.
 *
 * <p>Every operation opens its own connection and closes it when done. The
 * application used to share one connection across every thread, and a
 * transaction on it - {@code setAutoCommit(false)} - swept in whatever another
 * thread ran at that moment, rolling that work back too if the transaction
 * failed. SQLite in WAL mode serves several connections at once, and
 * {@code busy_timeout} makes a writer wait its turn instead of failing.</p>
 *
 * <p>Every connection gets the same per-connection settings, which SQLite
 * otherwise forgets:</p>
 * <ul>
 *   <li>{@code foreign_keys = ON} - without it the schema's {@code ON DELETE}
 *       clauses are ignored and deleting an animal would orphan its vaccines.</li>
 *   <li>{@code journal_mode = WAL} - readers and a writer proceed together.</li>
 *   <li>{@code busy_timeout = 5000} - wait up to 5 s for a lock instead of
 *       failing at once with "database is locked".</li>
 *   <li>Transactions begin {@code IMMEDIATE}. A deferred transaction that reads
 *       before it writes holds a read snapshot; if another connection commits in
 *       between, SQLite cannot upgrade it and fails with {@code SQLITE_BUSY} at
 *       once, ignoring {@code busy_timeout}. Taking the write lock up front makes
 *       concurrent writers queue instead.</li>
 * </ul>
 */
public final class Database {

    /**
     * Overridable so tests run against a scratch folder instead of the person's
     * real records. Also where the log files go, so a person can be asked for one
     * folder and send both.
     */
    static final Path DATA_DIR = Path.of(System.getProperty("animalesdeasis.data.dir",
            Path.of(System.getProperty("user.home"), ".asociaciondeasis").toString()));

    private static final DataSource DATA_SOURCE = newDataSource(DATA_DIR.resolve("AsociacionDeAsis.db"));

    private static final int BUSY_TIMEOUT_MS = 5000;

    private Database() {
    }

    /** Connections to the application's database. Close each one when done. */
    public static DataSource dataSource() {
        return DATA_SOURCE;
    }

    /** Connections to the database in {@code file}, with the settings described above. */
    public static DataSource newDataSource(Path file) {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + file);
        return dataSource;
    }
}
