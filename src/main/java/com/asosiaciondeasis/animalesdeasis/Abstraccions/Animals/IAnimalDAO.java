package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import java.util.List;

public interface IAnimalDAO {

    void insertAnimal(Animal animal) throws Exception;

    List<Animal> getAllAnimals() throws Exception;

    /**
     * Finds an animal by its unique ID or Filters.
     * @return The Animal object if found, otherwise null.
     */
    Animal findByChipNumber(String chipNumber) throws Exception;
    Animal findByBarcode(String barcode) throws Exception;
    List<Animal> findByFilters(String species, String startDate, String endDate, Boolean adopted) throws Exception;

    void updateAnimal(Animal animal) throws Exception;

    void deleteAnimal(String recordNumber) throws Exception;

    void reactivateAnimal(String recordNumber) throws Exception;

    List<Animal> getUnsyncedAnimals() throws Exception;
}
