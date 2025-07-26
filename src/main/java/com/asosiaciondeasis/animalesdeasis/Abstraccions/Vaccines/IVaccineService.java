package com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import java.util.List;

public interface IVaccineService {

    void registerVaccine(Vaccine vaccine) throws Exception;

    List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception;

    void updateVaccine(Vaccine vaccine) throws Exception;

    void deleteVaccine(int id) throws Exception;
}
