package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.util.List;

/** Animal operations as the interface sees them. */
public interface IAnimalService {

    /** @throws DuplicateChipException when the chip number or barcode is already taken */
    void registerAnimal(Animal animal) throws Exception;

    List<Animal> getActiveAnimals() throws Exception;

    Animal findByRecordNumber(String recordNumber) throws Exception;

    List<Animal> findByFilters(String species, String startDate, String endDate, String chipNumber, Boolean showInactive) throws Exception;

    /**
     * Saves a local edit and stamps it as modified now, so the next sync pushes it.
     *
     * @throws DuplicateChipException when the chip number or barcode is already taken
     */
    void updateAnimal(Animal animal) throws Exception;

    void deleteAnimal(String recordNumber) throws Exception;

    void reactivateAnimal(String recordNumber) throws Exception;
}
