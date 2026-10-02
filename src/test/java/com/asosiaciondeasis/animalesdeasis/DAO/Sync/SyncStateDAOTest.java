package com.asosiaciondeasis.animalesdeasis.DAO.Sync;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.SyncState;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncStateDAOTest {

    private static final Instant READ = Instant.parse("2026-06-01T12:00:00.123456789Z");
    private static final Instant FULL = Instant.parse("2026-05-20T08:30:00Z");

    private TestSupport.TestDatabase db;
    private SyncStateDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        db = TestSupport.newDatabase();
        dao = new SyncStateDAO(db.dataSource());
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void aNewInstallationHasReadNothing() throws Exception {
        assertEquals(SyncState.none(), dao.load());
    }

    /** To the nanosecond: the server's stamps are compared against this value. */
    @Test
    void whatWasSavedIsWhatIsLoaded() throws Exception {
        dao.save(new SyncState(READ, FULL));

        assertEquals(new SyncState(READ, FULL), dao.load());
    }

    @Test
    void savingAgainReplacesTheEarlierState() throws Exception {
        dao.save(new SyncState(FULL, FULL));
        dao.save(new SyncState(READ, FULL));

        assertEquals(new SyncState(READ, FULL), dao.load());
    }

    /** A damaged value must cost one full pull, not synchronisation itself. */
    @Test
    void anUnreadableValueCountsAsAbsent() throws Exception {
        dao.save(new SyncState(READ, FULL));
        try (Statement stmt = db.connection().createStatement()) {
            stmt.executeUpdate("UPDATE sync_state SET value = 'yesterday' WHERE key = 'read_up_to'");
        }

        assertEquals(new SyncState(null, FULL), dao.load());
    }
}
