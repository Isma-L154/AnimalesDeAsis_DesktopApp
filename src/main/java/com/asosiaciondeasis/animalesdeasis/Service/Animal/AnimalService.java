package com.asosiaciondeasis.animalesdeasis.Service.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.*;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;

import java.util.List;

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
    public Animal findByChipNumber(String chipNumber) throws Exception {
        return animalDAO.findByChipNumber(chipNumber);
    }

    @Override
    public Animal findByBarcode(String barcode) throws Exception {
        return animalDAO.findByBarcode(barcode);
    }

    @Override
    public List<Animal> findByFilters(String species, String startDate, String endDate, Boolean adopted) throws Exception {
        /**
         * In here we change the format of the date, calling the class that we have on 'DateUtils'
         * */
        String isoStartDate = (startDate != null) ? DateUtils.convertToIsoFormat(startDate) : null;
        String isoEndDate = (endDate != null) ? DateUtils.convertToIsoFormat(endDate) : null;

        return animalDAO.findByFilters(species, isoStartDate, isoEndDate, adopted);
    }

    @Override
    public void updateAnimal(Animal animal) throws Exception {

        animalDAO.updateAnimal(animal);
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
