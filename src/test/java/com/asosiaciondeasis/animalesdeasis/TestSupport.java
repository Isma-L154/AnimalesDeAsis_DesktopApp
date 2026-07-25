package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Config.DatabaseConnection;
import com.asosiaciondeasis.animalesdeasis.Config.SQLiteSetup;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared helpers for the test suite: builds an isolated in-memory SQLite database
 * (schema + PRAGMAs identical to production) and small object factories.
 */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * Opens a fresh in-memory SQLite connection with foreign keys enabled and the
     * full application schema created. Each call is fully isolated.
     */
    public static Connection newInMemoryDatabase() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        DatabaseConnection.applyPragmas(conn);
        SQLiteSetup.createSchema(conn);
        return conn;
    }

    /**
     * Inserts a province + place and returns the generated place id, so animals
     * (whose {@code place_id} FK is now enforced) can be inserted in tests.
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
