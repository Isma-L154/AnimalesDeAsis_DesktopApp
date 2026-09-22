package com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Persistence of vaccines, including the deletions that still have to reach Firebase. */
public interface IVaccineDAO {

    void insertVaccine(Vaccine vaccine) throws Exception;

    List<Vaccine> getVaccinesByAnimal(String animalRecordNumber) throws Exception;

    /** @return the vaccine, or {@code null} when none has that id */
    Vaccine findById(String id) throws Exception;

    /** Saves a local edit and stamps {@code last_modified} now, so the next sync pushes it. */
    void updateVaccine(Vaccine vaccine) throws Exception;

    /**
     * Deletes a vaccine and records the deletion, in one transaction, so it
     * survives until it has been applied to Firebase.
     */
    void deleteVaccine(String id) throws Exception;

    // --- Synchronisation ------------------------------------------------------

    List<Vaccine> getAllUnsyncedVaccines() throws Exception;

    /** Every local vaccine, for comparing against a pull. */
    List<Vaccine> getAllVaccines() throws Exception;

    /**
     * Inserts or replaces vaccines pulled from Firebase, in one transaction. An
     * existing row is replaced only while its {@code last_modified} still equals
     * {@code expectedLastModified.get(id)}, so a local edit made in the meantime
     * is kept.
     */
    void saveFromRemote(List<Vaccine> vaccines, Map<String, String> expectedLastModified) throws Exception;

    /**
     * Removes vaccines that were deleted in Firebase, in one transaction. No
     * tombstone is written - the deletion already happened remotely - and a row
     * edited locally since its last sync is kept.
     */
    void deleteRemovedRemotely(Collection<String> ids) throws Exception;

    /**
     * Marks pushed vaccines as synced, but only rows that still hold exactly what
     * was pushed, so an edit made during the upload is not lost.
     *
     * @return how many rows were marked
     */
    int markSynced(List<Vaccine> pushed) throws Exception;

    /** Vaccines deleted here and not yet deleted remotely: id to owning animal's record number. */
    Map<String, String> getPendingDeletions() throws Exception;

    /** Drops the tombstones of deletions that have been applied remotely. */
    void clearPendingDeletions(Collection<String> vaccineIds) throws Exception;
}
