package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.util.List;

public interface IAnimalDAO {

    boolean insertAnimal(Animal animal) throws Exception;

    List<Animal> getAllAnimals() throws Exception;

    Animal findByRecordNumber(String recordNumber) throws Exception;

    /**
     * Finds an animal by its unique ID or Filters.
     *
     * @return The Animal object if found, otherwise null.
     */

    List<Animal> findByFilters(String species, String startDate, String endDate, String chipNumber ,Boolean showInactive) throws Exception;

    boolean updateAnimal(Animal animal, boolean timestamp) throws Exception;

    void deleteAnimal(String recordNumber) throws Exception;

    void reactivateAnimal(String recordNumber) throws Exception;

    List<Animal> getUnsyncedAnimals() throws Exception;

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
