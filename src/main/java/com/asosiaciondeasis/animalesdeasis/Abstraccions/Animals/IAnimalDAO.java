package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.RowVersion;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.util.List;
import java.util.Map;

/** Persistence of animals. Every failure is thrown; nothing reports success it did not have. */
public interface IAnimalDAO {

    /** @throws DuplicateChipException when the chip number or barcode is already taken */
    void insertAnimal(Animal animal) throws Exception;

    /** Active animals, newest admission first. */
    List<Animal> getAllAnimals() throws Exception;

    /** @return the animal, or {@code null} when no record has that number */
    Animal findByRecordNumber(String recordNumber) throws Exception;

    /**
     * Animals matching every filter that is given; {@code null} or blank filters
     * are ignored. Dates are {@code yyyy-MM-dd}.
     */
    List<Animal> findByFilters(String species, String startDate, String endDate, String chipNumber, Boolean showInactive) throws Exception;

    /**
     * Saves a local edit and stamps {@code last_modified} now, so the next sync
     * pushes it.
     *
     * @throws DuplicateChipException when the chip number or barcode is already taken
     */
    void updateAnimal(Animal animal) throws Exception;

    /** Logical delete: the record is flagged inactive and queued for sync. */
    void deleteAnimal(String recordNumber) throws Exception;

    void reactivateAnimal(String recordNumber) throws Exception;

    // --- Synchronisation ------------------------------------------------------

    List<Animal> getUnsyncedAnimals() throws Exception;

    /** The current version of every animal, active or not, keyed by record number. */
    Map<String, RowVersion> getRowVersions() throws Exception;

    /**
     * Inserts or replaces records pulled from Firebase, in one transaction.
     *
     * <p>An existing row is replaced only while it still matches
     * {@code expected.get(recordNumber)} - the version the caller compared
     * against. An edit saved locally in the meantime is kept, and the next sync
     * resolves it.</p>
     */
    void saveFromRemote(List<Animal> animals, Map<String, RowVersion> expected) throws Exception;

    /**
     * Marks pushed records as synced, in one transaction, but only rows that still
     * hold exactly what was pushed. A row edited while the upload was in flight
     * stays unsynced, so the edit is pushed next time instead of being lost.
     *
     * @return how many rows were marked
     */
    int markSynced(List<Animal> pushed) throws Exception;

    // --- Home panel ---------------------------------------------------------
    // These count and cap in SQL rather than loading every row and measuring it
    // in Java. The panel runs all of them at once when the application opens, so
    // each one returning a number instead of a list is the difference between a
    // panel that appears and one that is felt.

    /** Animals currently present: active and not adopted. */
    int countInShelter() throws Exception;

    /** Animals adopted whose admission date falls in {@code year}. */
    int countAdoptedInYear(int year) throws Exception;

    /** Most recently admitted active animals, newest first. */
    List<Animal> getRecentAdmissions(int limit) throws Exception;

    /** Active animals with no vaccination on record, capped at {@code limit}. */
    List<Animal> findWithoutVaccines(int limit) throws Exception;

    /** Active animals with no chip number, capped at {@code limit}. */
    List<Animal> findWithoutChip(int limit) throws Exception;

    /** How many records are waiting to be pushed to Firebase. */
    int countUnsynced() throws Exception;
}
