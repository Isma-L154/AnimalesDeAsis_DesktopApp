package com.asosiaciondeasis.animalesdeasis.Model;

import java.util.UUID;

public class Vaccine {

    private String id;
    private String animalRecordNumber;
    private String vaccineName;
    private String vaccinationDate;
    private boolean synced;
    private String lastModified;


    public Vaccine() {}

    private Vaccine(String id) {this.id = id;}

    public static Vaccine createNew() {return new Vaccine(UUID.randomUUID().toString());}

    public static Vaccine fromExistingRecord(String id) {return new Vaccine(id);}



    public String getId() {
        return id;
    }

    public String getAnimalRecordNumber() {return animalRecordNumber;}

    public void setAnimalRecordNumber(String animalRecordNumber) {
        this.animalRecordNumber = animalRecordNumber;
    }

    public String getVaccineName() {
        return vaccineName;
    }

    public void setVaccineName(String vaccineName) {
        this.vaccineName = vaccineName;
    }

    public String getVaccinationDate() {
        return vaccinationDate;
    }

    public void setVaccinationDate(String vaccinationDate) {
        this.vaccinationDate = vaccinationDate;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
}
