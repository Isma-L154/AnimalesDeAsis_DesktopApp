package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.util.List;

public interface IAnimalService {
    boolean registerAnimal(Animal animal) throws Exception;

    List<Animal> getActiveAnimals() throws Exception;

    Animal findByRecordNumber(String recordNumber) throws Exception;

    List<Animal> findByFilters(String species, String startDate, String endDate, String chipNumber ,Boolean showInactive) throws Exception;

    boolean updateAnimal(Animal animal, boolean timestamp) throws Exception;

    void deleteAnimal(String recordNumber) throws Exception;

    void reactivateAnimal(String recordNumber) throws Exception;
}
