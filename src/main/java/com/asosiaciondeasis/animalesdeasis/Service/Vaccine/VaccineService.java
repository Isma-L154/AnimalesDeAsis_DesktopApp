package com.asosiaciondeasis.animalesdeasis.Service.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineService;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.util.List;

public class VaccineService implements IVaccineService {
    private final IVaccineDAO vaccineDAO;

    // Constructor injection for better testability and decoupling
    public VaccineService(IVaccineDAO vaccineDAO) {
        this.vaccineDAO = vaccineDAO;
    }

    @Override
    public void registerVaccine(Vaccine vaccine) throws Exception {
        vaccineDAO.insertVaccine(vaccine);
    }

    @Override
    public List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception {
        return vaccineDAO.getVaccinesByAnimal(animalRecordNumber);
    }

    @Override
    public void updateVaccine(Vaccine vaccine, boolean timestamp) throws Exception {
        vaccineDAO.updateVaccine(vaccine, true);
    }

    @Override
    public void deleteVaccine(String id) throws Exception {
        vaccineDAO.deleteVaccine(id);
    }

    @Override
    public Vaccine existsVaccine(String id) throws Exception {
        return vaccineDAO.existsVaccine(id);
    }
}
