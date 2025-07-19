package com.asosiaciondeasis.animalesdeasis.Service.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.*;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import java.util.List;
import java.util.UUID;

public class AnimalService implements IAnimalService{

    private final IAnimalDAO animalDAO;

    public AnimalService(IAnimalDAO animalDAO) {
        this.animalDAO = animalDAO;
    }

    @Override
    public void registerAnimal(Animal animal) throws Exception {

        animalDAO.insertAnimal(animal);
    }

    @Override
    public List<Animal> getActiveAnimals() throws Exception {
        return animalDAO.getAllAnimals();
    }

    @Override
    public void updateAnimal(Animal animal) throws Exception {

        animalDAO.updateAnimal(animal);
    }

    @Override
    public void deleteAnimal(String recordNumber) throws Exception {

        animalDAO.deleteAnimal(recordNumber);
    }
}
