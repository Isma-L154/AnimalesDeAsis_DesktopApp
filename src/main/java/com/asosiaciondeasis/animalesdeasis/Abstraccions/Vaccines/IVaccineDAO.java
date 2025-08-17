package com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.util.List;

public interface IVaccineDAO {

    void insertVaccine(Vaccine vaccine) throws Exception;

    List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception;

    void updateVaccine(Vaccine vaccine, boolean timestamp) throws Exception;

    void deleteVaccine(String id) throws Exception;

    List<Vaccine> getAllUnsyncedVaccines() throws Exception;

    Vaccine existsVaccine(String id) throws Exception;
}
