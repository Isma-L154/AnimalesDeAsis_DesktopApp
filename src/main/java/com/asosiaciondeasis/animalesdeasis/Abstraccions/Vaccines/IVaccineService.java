package com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.util.List;

/** Vaccine operations as the interface sees them. */
public interface IVaccineService {

    void registerVaccine(Vaccine vaccine) throws Exception;

    List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception;

    /** Saves a local edit and stamps it as modified now, so the next sync pushes it. */
    void updateVaccine(Vaccine vaccine) throws Exception;
}
