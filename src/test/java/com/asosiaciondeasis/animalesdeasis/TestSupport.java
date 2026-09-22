package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Config.Database;
import com.asosiaciondeasis.animalesdeasis.Config.SQLiteSetup;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared helpers for the test suite: isolated databases with the production
 * schema and settings, and small object factories.
 */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * A scratch database in a temporary file, opened through the same data
     * source settings as production.
     *
     * <p>A file rather than {@code :memory:}: the DAOs open a connection per
     * call, and every connection to {@code :memory:} is a separate, empty
     * database. A file also exercises WAL and locking as they really behave.</p>
     *
     * @param dataSource what the code under test should use
     * @param connection an open connection for the test's own setup and checks
     */
    public record TestDatabase(DataSource dataSource, Connection connection, Path file) implements AutoCloseable {

        @Override
        public void close() throws SQLException, IOException {
            connection.close();
            Files.deleteIfExists(file);
            Files.deleteIfExists(Path.of(file + "-wal"));
            Files.deleteIfExists(Path.of(file + "-shm"));
        }
    }

    public static TestDatabase newDatabase() throws SQLException, IOException {
        Path file = Files.createTempFile("animalesdeasis-test", ".db");
        DataSource dataSource = Database.newDataSource(file);
        Connection connection = dataSource.getConnection();
        SQLiteSetup.createSchema(connection);
        return new TestDatabase(dataSource, connection, file);
    }

    /**
     * Inserts a province + place and returns the generated place id, so animals
     * (whose {@code place_id} FK is enforced) can be inserted in tests.
     */
    public static int seedPlace(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO provinces (name) VALUES ('San José')");
            stmt.executeUpdate("INSERT INTO places (name, province_id) VALUES ('Central', 1)");
            try (var rs = stmt.executeQuery("SELECT id FROM places LIMIT 1")) {
                rs.next();
                return rs.getInt("id");
            }
        }
    }

    /** Builds a valid, minimally-populated animal for the given place. */
    public static Animal newAnimal(int placeId) {
        Animal animal = Animal.createNew();
        animal.setAdmissionDate("2024-01-15");
        animal.setPlaceId(placeId);
        animal.setSpecies("Perro");
        animal.setSex("Macho");
        animal.setName("Firulais");
        animal.setApproximateAge(3);
        animal.setActive(true);
        animal.setSynced(false);
        return animal;
    }

    /** Builds a valid vaccine for the given animal record number. */
    public static Vaccine newVaccine(String animalRecordNumber) {
        Vaccine vaccine = Vaccine.createNew();
        vaccine.setAnimalRecordNumber(animalRecordNumber);
        vaccine.setVaccineName("Rabia");
        vaccine.setVaccinationDate("2024-02-01");
        vaccine.setSynced(false);
        return vaccine;
    }
}
