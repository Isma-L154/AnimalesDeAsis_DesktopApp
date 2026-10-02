package com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The shared copy of the records, as synchronisation needs it. */
public interface IRemoteRecords {

    /** Every animal, stamped or not. Costs one read per animal and per vaccine. */
    RemoteChanges fetchAll() throws Exception;

    /**
     * Only the animals stamped after {@code since}. Costs one read per changed
     * animal and per vaccine of those animals, however large the whole set is.
     */
    RemoteChanges fetchChangedSince(Instant since) throws Exception;

    /**
     * Uploads local changes and stamps every animal they touch with the
     * server's time, including the owner of each vaccine written or deleted.
     *
     * <p>An animal is stamped only after everything of its own in this call has
     * been written, so whoever sees the stamp also sees the changes behind it.</p>
     *
     * @param deletedVaccines id of each vaccine to delete, to its animal's record number
     */
    void push(List<Animal> animals, List<Vaccine> vaccines, Map<String, String> deletedVaccines) throws Exception;
}
