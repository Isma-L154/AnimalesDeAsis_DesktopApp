package com.asosiaciondeasis.animalesdeasis.Service.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalService;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;

import java.util.List;

public class AnimalService implements IAnimalService {

    private final IAnimalDAO animalDAO;

    public AnimalService(IAnimalDAO animalDAO) {
        this.animalDAO = animalDAO;
    }

    @Override
    public boolean registerAnimal(Animal animal) throws Exception {

        animalDAO.insertAnimal(animal);
        return true;
    }

    @Override
    public List<Animal> getActiveAnimals() throws Exception {
        return animalDAO.getAllAnimals();
    }

    @Override
    public Animal findByRecordNumber(String recordNumber) throws Exception {
        return animalDAO.findByRecordNumber(recordNumber);
    }

    @Override
    public List<Animal> findByFilters(String species, String startDate, String endDate, Boolean showInactive) throws Exception {
        return animalDAO.findByFilters(species, startDate, endDate, showInactive);
    }

    @Override
    public boolean updateAnimal(Animal animal) throws Exception {
        animalDAO.updateAnimal(animal);
        return true;
    }

    @Override
    public void deleteAnimal(String recordNumber) throws Exception {
        animalDAO.deleteAnimal(recordNumber);
    }

    @Override
    public void reactivateAnimal(String recordNumber) throws Exception {
        animalDAO.reactivateAnimal(recordNumber);
    }
}
