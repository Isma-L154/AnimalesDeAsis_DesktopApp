package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import java.util.List;

public interface IAnimalService {
    void registerAnimal(Animal animal) throws Exception;
    List<Animal> getActiveAnimals() throws Exception;
    void updateAnimal(Animal animal) throws Exception;
    void deleteAnimal(String recordNumber) throws Exception;
}
