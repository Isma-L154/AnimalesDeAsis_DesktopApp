package com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.util.List;

public interface IVaccineService {

    void registerVaccine(Vaccine vaccine) throws Exception;

    List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception;

    void updateVaccine(Vaccine vaccine, boolean timestamp) throws Exception;

    void deleteVaccine(String id) throws Exception;

    Vaccine existsVaccine(String id) throws Exception;
}
