package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every thread used to share one connection, so a transaction opened by one
 * thread swept in whatever another thread ran meanwhile - and rolled it back
 * with its own work if it failed. Each operation now has its own connection.
 */
class ConnectionIsolationTest {

    private TestSupport.TestDatabase db;
    private AnimalDAO animalDAO;
    private VaccineDAO vaccineDAO;
    private int placeId;

    @BeforeEach
    void setUp() throws Exception {
        db = TestSupport.newDatabase();
        placeId = TestSupport.seedPlace(db.connection());
        animalDAO = new AnimalDAO(db.dataSource());
        vaccineDAO = new VaccineDAO(db.dataSource());
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void aRolledBackTransactionDoesNotTakeAnotherThreadsWriteWithIt() throws Exception {
        Animal written = TestSupport.newAnimal(placeId);
        CompletableFuture<Void> otherThread;

        try (Connection transaction = db.dataSource().getConnection()) {
            transaction.setAutoCommit(false);
            try (Statement stmt = transaction.createStatement()) {
                stmt.executeUpdate("INSERT INTO provinces (name) VALUES ('Descartada')");
            }

            // Waits for the transaction's write lock (busy_timeout), then commits
            // on its own connection.
            otherThread = CompletableFuture.runAsync(() -> {
                try {
                    animalDAO.insertAnimal(written);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Thread.sleep(200);
            transaction.rollback();
            // sqlite-jdbc reopens a transaction after rollback while auto-commit is
            // off; switching it back on is what actually releases the lock.
            transaction.setAutoCommit(true);
        }
        otherThread.get(10, TimeUnit.SECONDS);

        assertNotNull(animalDAO.findByRecordNumber(written.getRecordNumber()),
                "the other thread's insert must survive the rollback");
        assertEquals(0, count("SELECT COUNT(*) FROM provinces WHERE name = 'Descartada'"));
    }

    @Test
    void concurrentInsertsAndDeletesAllComplete() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animalDAO.insertAnimal(animal);

        int threads = 4;
        int perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            results.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
                    vaccineDAO.insertVaccine(vaccine);
                    if (i % 2 == 0) {
                        vaccineDAO.deleteVaccine(vaccine.getId());
                    }
                }
                return null;
            }));
        }
        for (Future<?> result : results) {
            result.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        int deletedPerThread = (perThread + 1) / 2;
        assertEquals(threads * (perThread - deletedPerThread),
                vaccineDAO.getVaccinesByAnimal(animal.getRecordNumber()).size());
        assertEquals(threads * deletedPerThread, vaccineDAO.getPendingDeletions().size(),
                "every deletion kept its tombstone");
    }

    private int count(String sql) throws Exception {
        try (Statement stmt = db.connection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
