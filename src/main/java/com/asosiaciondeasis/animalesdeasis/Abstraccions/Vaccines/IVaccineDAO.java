package com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.util.List;

public interface IVaccineDAO {

    void insertVaccine(Vaccine vaccine) throws Exception;

    List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception;

    void updateVaccine(Vaccine vaccine) throws Exception;

    void deleteVaccine(int id) throws Exception;

    List<Vaccine> getAllUnsyncedVaccines() throws Exception;

    boolean existsVaccine(int id) throws Exception;
}
