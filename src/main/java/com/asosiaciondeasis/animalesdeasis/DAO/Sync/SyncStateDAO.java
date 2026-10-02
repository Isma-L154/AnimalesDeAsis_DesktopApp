package com.asosiaciondeasis.animalesdeasis.DAO.Sync;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.ISyncStateDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.SyncState;
import com.asosiaciondeasis.animalesdeasis.DAO.Transactions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/** SQLite implementation of {@link ISyncStateDAO}, over the {@code sync_state} key-value table. */
public class SyncStateDAO implements ISyncStateDAO {

    private static final String READ_UP_TO = "read_up_to";
    private static final String LAST_FULL_PULL = "last_full_pull";

    private final DataSource dataSource;

    public SyncStateDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public SyncState load() throws Exception {
        Map<String, String> values = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT key, value FROM sync_state");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                values.put(rs.getString(1), rs.getString(2));
            }
        }
        return new SyncState(parse(values.get(READ_UP_TO)), parse(values.get(LAST_FULL_PULL)));
    }

    @Override
    public void save(SyncState state) throws Exception {
        Transactions.inTransaction(dataSource, conn -> {
            store(conn, READ_UP_TO, state.readUpTo());
            store(conn, LAST_FULL_PULL, state.lastFullPull());
            return null;
        });
    }

    private static void store(Connection conn, String key, Instant value) throws SQLException {
        if (value == null) {
            return;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO sync_state (key, value) VALUES (?, ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            pstmt.setString(1, key);
            pstmt.setString(2, value.toString());
            pstmt.executeUpdate();
        }
    }

    /**
     * An unreadable value counts as absent. The consequence is one full pull,
     * which is always safe; failing instead would stop synchronisation for good.
     */
    private static Instant parse(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return Instant.parse(stored);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
