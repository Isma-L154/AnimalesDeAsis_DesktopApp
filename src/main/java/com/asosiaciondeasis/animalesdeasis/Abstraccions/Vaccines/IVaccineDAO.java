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

    // --- Deletions that still have to reach Firebase -------------------------
    // Vaccines are hard-deleted, so without a record of the deletion the next
    // pull finds the row still in Firebase and puts it back.

    /** Vaccine ids deleted here whose deletion has not yet been applied remotely. */
    java.util.List<String> getPendingDeletions() throws Exception;

    /** Marks a deletion as applied remotely, so the tombstone can go. */
    void clearPendingDeletion(String vaccineId) throws Exception;

    /** The animal a pending deletion belonged to, needed to address the remote document. */
    String getPendingDeletionAnimal(String vaccineId) throws Exception;
}
