package com.asosiaciondeasis.animalesdeasis.Model;

import java.util.UUID;

/**
 * Represents an animal that has been rescued and registered in the system.
 */
public class Animal {

    private final String recordNumber; // UUID
    private String chipNumber;
    private String barcode;
    private String admissionDate; // Format: DD-MM-YYYY
    private String collectedBy;
    private int placeId;
    private String reasonForRescue;
    private String species; // 'Perro' or 'Gato'
    private int approximateAge;
    private String sex; // 'Macho' or 'Hembra'
    private String name;
    private String ailments;
    private String neuteringDate;  // Format: DD-MM-YYYY
    private boolean adopted;



    private Animal(String recordNumber) {
        this.recordNumber = recordNumber;
    }


    public static Animal createNew() {
        return new Animal(UUID.randomUUID().toString());
    }


    public static Animal fromExistingRecord(String recordNumber) {
        return new Animal(recordNumber);
    }


    /**
     * Getters and Setters
     * */
    public String getRecordNumber() {
        return recordNumber;
    }

    public String getChipNumber() {
        return chipNumber;
    }

    public void setChipNumber(String chipNumber) {
        this.chipNumber = chipNumber;
    }

    public String getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(String admissionDate) {
        this.admissionDate = admissionDate;
    }

    public String getCollectedBy() {
        return collectedBy;
    }

    public void setCollectedBy(String collectedBy) {
        this.collectedBy = collectedBy;
    }

    public int getPlaceId() {
        return placeId;
    }

    public void setPlaceId(int placeId) {
        this.placeId = placeId;
    }

    public String getReasonForRescue() {
        return reasonForRescue;
    }

    public void setReasonForRescue(String reasonForRescue) {
        this.reasonForRescue = reasonForRescue;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public int getApproximateAge() {
        return approximateAge;
    }

    public void setApproximateAge(int approximateAge) {
        this.approximateAge = approximateAge;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAilments() {
        return ailments;
    }

    public void setAilments(String ailments) {
        this.ailments = ailments;
    }

    public String getNeuteringDate() {
        return neuteringDate;
    }

    public void setNeuteringDate(String neuteringDate) {
        this.neuteringDate = neuteringDate;
    }

    public boolean isAdopted() {
        return adopted;
    }

    public void setAdopted(boolean adopted) {
        this.adopted = adopted;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
}
