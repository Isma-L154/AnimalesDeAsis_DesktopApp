package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import java.util.List;

public interface IAnimalService {
    boolean registerAnimal(Animal animal) throws Exception;
    List<Animal> getActiveAnimals() throws Exception;
    Animal findByChipNumber(String chipNumber) throws Exception;
    Animal findByBarcode(String barcode) throws Exception;
    List<Animal> findByFilters(String species, String startDate, String endDate, Boolean adopted) throws Exception;
    void updateAnimal(Animal animal) throws Exception;
    void deleteAnimal(String recordNumber) throws Exception;
    void reactivateAnimal(String recordNumber) throws Exception;
}
