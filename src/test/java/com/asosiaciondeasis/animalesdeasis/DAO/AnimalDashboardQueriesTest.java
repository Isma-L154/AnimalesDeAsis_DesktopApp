package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.ShelterSummary;
import com.asosiaciondeasis.animalesdeasis.Service.Home.ShelterSummaryService;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queries behind the home panel.
 *
 * <p>Worth testing carefully because each one encodes a definition — what counts
 * as "in the shelter", what counts as "missing a chip" — and those definitions
 * are what someone reads off the screen and acts on. A count that quietly
 * includes adopted animals is not a cosmetic bug.</p>
 */
class AnimalDashboardQueriesTest {

    private Connection conn;
    private AnimalDAO dao;
    private VaccineDAO vaccineDAO;
    private int placeId;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestSupport.newInMemoryDatabase();
        placeId = TestSupport.seedPlace(conn);
        dao = new AnimalDAO(conn);
        vaccineDAO = new VaccineDAO(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private Animal insert(String name, boolean adopted, boolean active, String admission)
            throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setName(name);
        animal.setAdopted(adopted);
        animal.setActive(active);
        animal.setAdmissionDate(admission);
        dao.insertAnimal(animal);
        if (!active) {
            // insertAnimal always writes an active row; soft-delete is a separate step.
            dao.deleteAnimal(animal.getRecordNumber());
        }
        return animal;
    }

    @Test
    @DisplayName("in-shelter counts the present animals only")
    void inShelterExcludesAdoptedAndDeleted() throws Exception {
        insert("Presente", false, true, "2025-01-10T00:00:00");
        insert("TambiénPresente", false, true, "2025-02-10T00:00:00");
        insert("Adoptado", true, true, "2025-03-10T00:00:00");
        insert("Borrado", false, false, "2025-04-10T00:00:00");

        assertEquals(2, dao.countInShelter(),
                "an adopted or soft-deleted animal is not in the shelter");
    }

    @Test
    @DisplayName("adoptions are counted per year, not in total")
    void adoptedIsScopedToTheYear() throws Exception {
        insert("EsteAno", true, true, "2025-05-10T00:00:00");
        insert("EsteAnoTambien", true, true, "2025-06-10T00:00:00");
        insert("AnoPasado", true, true, "2024-05-10T00:00:00");

        assertEquals(2, dao.countAdoptedInYear(2025));
        assertEquals(1, dao.countAdoptedInYear(2024));
        assertEquals(0, dao.countAdoptedInYear(2023));
    }

    /**
     * admission_date is stored as ISO 8601 despite SQLiteSetup describing it as
     * DD-MM-YYYY. That comment is wrong, and it matters: under DD-MM-YYYY this
     * ordering would sort by day of the month, so January the 30th would come
     * out ahead of December the 2nd.
     */
    @Test
    @DisplayName("recent admissions come back newest first")
    void recentAdmissionsAreOrderedByDate() throws Exception {
        insert("Viejo", false, true, "2025-01-02T00:00:00");
        insert("Nuevo", false, true, "2025-12-30T00:00:00");
        insert("Medio", false, true, "2025-06-15T00:00:00");

        List<Animal> recent = dao.getRecentAdmissions(10);

        assertEquals(List.of("Nuevo", "Medio", "Viejo"),
                recent.stream().map(Animal::getName).toList());
    }

    @Test
    @DisplayName("recent admissions respect the limit")
    void recentAdmissionsAreCapped() throws Exception {
        for (int i = 1; i <= 8; i++) {
            insert("Animal" + i, false, true, "2025-0" + (i % 9) + "-01T00:00:00");
        }
        assertEquals(3, dao.getRecentAdmissions(3).size());
    }

    @Test
    @DisplayName("animals with a vaccine are not listed as missing one")
    void missingVaccinesExcludesVaccinated() throws Exception {
        Animal vaccinated = insert("Vacunado", false, true, "2025-01-10T00:00:00");
        insert("SinVacunas", false, true, "2025-02-10T00:00:00");
        vaccineDAO.insertVaccine(TestSupport.newVaccine(vaccinated.getRecordNumber()));

        List<Animal> missing = dao.findWithoutVaccines(10);

        assertEquals(1, missing.size());
        assertEquals("SinVacunas", missing.get(0).getName());
    }

    /**
     * A chip that was never filled in arrives as NULL from the form and as an
     * empty string from an import. Treating only one of those as missing would
     * silently under-report exactly the records that need attention.
     */
    @Test
    @DisplayName("a blank chip counts as missing, whether null or empty")
    void missingChipCoversNullAndBlank() throws Exception {
        Animal withChip = TestSupport.newAnimal(placeId);
        withChip.setName("ConChip");
        withChip.setChipNumber("900123456789");
        dao.insertAnimal(withChip);

        Animal nullChip = TestSupport.newAnimal(placeId);
        nullChip.setName("ChipNulo");
        nullChip.setChipNumber(null);
        dao.insertAnimal(nullChip);

        Animal blankChip = TestSupport.newAnimal(placeId);
        blankChip.setName("ChipVacio");
        blankChip.setChipNumber("   ");
        dao.insertAnimal(blankChip);

        List<String> missing = dao.findWithoutChip(10).stream().map(Animal::getName).sorted().toList();

        assertEquals(List.of("ChipNulo", "ChipVacio"), missing);
    }

    @Test
    @DisplayName("unsynced counts only what is still waiting")
    void unsyncedCount() throws Exception {
        insert("Pendiente", false, true, "2025-01-10T00:00:00");
        Animal sent = insert("Enviado", false, true, "2025-02-10T00:00:00");

        // Read back before updating, which is what SyncService does: it marks the
        // rows returned by getUnsyncedAnimals(). It matters because
        // updateAnimal(animal, false) writes last_modified straight from the
        // object, and an in-memory animal that has never been read has none - the
        // update then fails on a NOT NULL constraint rather than saying so.
        Animal stored = dao.findByRecordNumber(sent.getRecordNumber());
        stored.setSynced(true);
        dao.updateAnimal(stored, false);

        assertEquals(1, dao.countUnsynced());
        assertFalse(dao.findByRecordNumber(
                dao.getUnsyncedAnimals().get(0).getRecordNumber()).isSynced());
    }

    @Test
    @DisplayName("the summary service gathers a consistent picture")
    void summaryServiceAssemblesEverything() throws Exception {
        int year = LocalDate.now().getYear();
        insert("EnAlbergue", false, true, year + "-01-10T00:00:00");
        insert("Adoptado", true, true, year + "-02-10T00:00:00");

        ShelterSummary summary = new ShelterSummaryService(dao).load();

        assertEquals(1, summary.inShelter());
        assertEquals(1, summary.adoptedThisYear());
        assertEquals(year, summary.year());
        assertEquals(50.0, summary.adoptionRate(), 0.001);
        assertTrue(summary.hasAttentionItems(), "neither animal has a vaccine on record");
    }

    @Test
    @DisplayName("an empty shelter reports zero rather than NaN")
    void adoptionRateOnAnEmptyShelter() throws Exception {
        ShelterSummary summary = new ShelterSummaryService(dao).load();

        assertEquals(0, summary.inShelter());
        assertEquals(0.0, summary.adoptionRate(), 0.001,
                "dividing by no animals must not put NaN% on the screen");
        assertFalse(summary.hasAttentionItems());
    }

    @Test
    @DisplayName("the summary's lists cannot be modified by their holder")
    void summaryListsAreImmutable() throws Exception {
        insert("Alguno", false, true, "2025-01-10T00:00:00");
        ShelterSummary summary = new ShelterSummaryService(dao).load();

        assertThrows(UnsupportedOperationException.class,
                () -> summary.recentAdmissions().clear(),
                "the summary crosses a thread boundary; it must not be mutable on the far side");
    }
}
