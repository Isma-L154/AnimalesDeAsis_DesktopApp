package com.asosiaciondeasis.animalesdeasis.DAO.Sync;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.IRemoteRecords;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteAnimal;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteChanges;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Firestore implementation of {@link IRemoteRecords}: an {@code animals}
 * collection, each document with a {@code vaccines} subcollection.
 *
 * <p>Only animal documents carry the {@link #SYNCED_AT} stamp. A change to a
 * vaccine stamps its animal instead, so one query on a single collection finds
 * everything that changed and no collection-group index has to be deployed.</p>
 */
public class FirestoreRecords implements IRemoteRecords {

    private static final String ANIMALS = "animals";
    private static final String VACCINES = "vaccines";
    static final String SYNCED_AT = "syncedAt";

    /**
     * Firestore commits at most 500 operations in one batch. Exceeding it fails
     * the whole commit, so the more work had accumulated offline, the more
     * certain it was that none of it would upload.
     */
    private static final int MAX_BATCH_OPERATIONS = 500;

    private final Supplier<Firestore> firestore;

    /** @param firestore asked on every call, so nothing is connected until a sync actually runs */
    public FirestoreRecords(Supplier<Firestore> firestore) {
        this.firestore = firestore;
    }

    @Override
    public RemoteChanges fetchAll() throws Exception {
        return fetch(firestore.get().collection(ANIMALS));
    }

    @Override
    public RemoteChanges fetchChangedSince(Instant since) throws Exception {
        Timestamp bound = Timestamp.ofTimeSecondsAndNanos(since.getEpochSecond(), since.getNano());
        return fetch(firestore.get().collection(ANIMALS).whereGreaterThan(SYNCED_AT, bound));
    }

    private static RemoteChanges fetch(Query animals) throws Exception {
        QuerySnapshot snapshot = animals.get().get();
        List<QueryDocumentSnapshot> documents = snapshot.getDocuments();

        // Vaccines are read after their animals. A vaccine written in between is
        // simply seen early: its animal's stamp is newer than this read, so the
        // next pull returns the animal again.
        List<ApiFuture<QuerySnapshot>> vaccineFutures = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            vaccineFutures.add(document.getReference().collection(VACCINES).get());
        }
        List<QuerySnapshot> vaccineSnapshots = ApiFutures.allAsList(vaccineFutures).get();

        List<RemoteAnimal> result = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            List<Vaccine> vaccines = new ArrayList<>();
            for (QueryDocumentSnapshot vaccine : vaccineSnapshots.get(i).getDocuments()) {
                vaccines.add(vaccine.toObject(Vaccine.class));
            }
            result.add(new RemoteAnimal(documents.get(i).toObject(Animal.class), vaccines));
        }
        Timestamp readTime = snapshot.getReadTime();
        return new RemoteChanges(result, Instant.ofEpochSecond(readTime.getSeconds(), readTime.getNanos()));
    }

    @Override
    public void push(List<Animal> animals, List<Vaccine> vaccines, Map<String, String> deletedVaccines)
            throws Exception {
        Firestore db = firestore.get();

        // Each entry applies itself to whichever batch it is handed, so no shared
        // mutable state is needed to assemble the chunks.
        List<Consumer<WriteBatch>> writes = new ArrayList<>();
        for (Animal animal : animals) {
            writes.add(batch -> batch.set(animalDocument(db, animal.getRecordNumber()), animal));
        }
        for (Vaccine vaccine : vaccines) {
            writes.add(batch -> batch.set(
                    vaccineDocument(db, vaccine.getAnimalRecordNumber(), vaccine.getId()), vaccine));
        }
        deletedVaccines.forEach((vaccineId, animalRecordNumber) -> writes.add(batch ->
                batch.delete(vaccineDocument(db, animalRecordNumber, vaccineId))));

        // Stamps go last. Chunks commit in order, so by the time an animal shows
        // up as changed, every write behind that stamp has already landed.
        for (String recordNumber : stampedAnimals(animals, vaccines, deletedVaccines)) {
            writes.add(batch -> batch.set(animalDocument(db, recordNumber),
                    Map.of(SYNCED_AT, FieldValue.serverTimestamp()), SetOptions.merge()));
        }

        for (List<Consumer<WriteBatch>> chunk : partition(writes, MAX_BATCH_OPERATIONS)) {
            WriteBatch batch = db.batch();
            for (Consumer<WriteBatch> write : chunk) {
                write.accept(batch);
            }
            // Awaited one at a time: a failure leaves the earlier chunks applied
            // and the caller's records unsynced, so the next run sends them
            // again. Every write here is idempotent.
            batch.commit().get();
        }
    }

    /**
     * The animals a push has to stamp: those written, and the owner of every
     * vaccine written or deleted. Each once, however many of its records changed.
     */
    static Set<String> stampedAnimals(List<Animal> animals, List<Vaccine> vaccines,
                                      Map<String, String> deletedVaccines) {
        Set<String> recordNumbers = new LinkedHashSet<>();
        for (Animal animal : animals) {
            recordNumbers.add(animal.getRecordNumber());
        }
        for (Vaccine vaccine : vaccines) {
            recordNumbers.add(vaccine.getAnimalRecordNumber());
        }
        recordNumbers.addAll(deletedVaccines.values());
        return recordNumbers;
    }

    /**
     * Splits {@code items} into consecutive groups of at most {@code size}.
     *
     * <p>Package-visible so the boundaries can be tested without Firestore.
     * Off-by-one here is the whole bug: one operation over the limit and the
     * commit fails entirely.</p>
     */
    static <T> List<List<T>> partition(List<T> items, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("chunk size must be positive, got " + size);
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < items.size(); start += size) {
            chunks.add(new ArrayList<>(items.subList(start, Math.min(start + size, items.size()))));
        }
        return chunks;
    }

    private static DocumentReference animalDocument(Firestore db, String recordNumber) {
        return db.collection(ANIMALS).document(recordNumber);
    }

    private static DocumentReference vaccineDocument(Firestore db, String animalRecordNumber, String vaccineId) {
        return animalDocument(db, animalRecordNumber).collection(VACCINES).document(vaccineId);
    }
}
