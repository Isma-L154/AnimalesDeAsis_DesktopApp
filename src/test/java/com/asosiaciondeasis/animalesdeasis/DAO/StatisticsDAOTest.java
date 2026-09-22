package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Statistics.StatisticsDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsDAOTest {

    private TestSupport.TestDatabase db;
    private Connection conn;
    private AnimalDAO animalDAO;
    private StatisticsDAO statisticsDAO;
    private int placeId;

    @BeforeEach
    void setUp() throws Exception {
        db = TestSupport.newDatabase();
        conn = db.connection();
        placeId = TestSupport.seedPlace(conn);
        animalDAO = new AnimalDAO(db.dataSource());
        statisticsDAO = new StatisticsDAO(db.dataSource());
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    private void admit(String admissionDate, boolean adopted) throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setAdmissionDate(admissionDate);
        animal.setAdopted(adopted);
        animalDAO.insertAnimal(animal);
    }

    /**
     * Admission dates are stored as UTC midnight. A 1 January admission belongs to
     * that year; it used to be shifted into the previous one on machines east of
     * Greenwich, because the query re-applied a UTC conversion. That shift depends
     * on the operating system's timezone, which a test cannot change for SQLite,
     * so this pins the boundary rather than reproducing the shift.
     */
    @Test
    void countsAnAdmissionInTheYearAndMonthOfItsDate() throws Exception {
        admit("2024-01-01T00:00:00", false);
        admit("2024-12-31T00:00:00", false);
        admit("2023-12-31T00:00:00", false);

        assertEquals(2, statisticsDAO.getTotalAdmissions(2024));
        assertEquals(Map.of("01", 1, "12", 1), statisticsDAO.getMonthlyAdmissions(2024));
    }

    @Test
    void adoptionRateIsTheShareOfTheYearsAdmissions() throws Exception {
        admit("2024-03-01T00:00:00", true);
        admit("2024-04-01T00:00:00", false);
        admit("2024-05-01T00:00:00", false);
        admit("2024-06-01T00:00:00", true);

        assertEquals(50.0, statisticsDAO.getAdoptionRate(2024), 1e-9);
    }

    @Test
    void adoptionRateIsZeroForAYearWithoutAdmissions() throws Exception {
        assertEquals(0.0, statisticsDAO.getAdoptionRate(2024), 1e-9);
    }

    @Test
    void originsNameThePlaceAndItsProvince() throws Exception {
        admit("2024-03-01T00:00:00", false);
        admit("2024-04-01T00:00:00", false);

        assertEquals(Map.of("Central, San José", 2), statisticsDAO.getAnimalOrigins(2024));
    }
}
