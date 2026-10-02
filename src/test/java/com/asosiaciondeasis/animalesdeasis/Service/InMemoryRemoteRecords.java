package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.IRemoteRecords;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteAnimal;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteChanges;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A shared copy held in memory, standing in for Firestore: several
 * installations can push to it and pull from it within one test.
 *
 * <p>Records are copied on the way in and on the way out, as a real remote
 * would serialise them, so no two installations ever share an object.</p>
 */
final class InMemoryRemoteRecords implements IRemoteRecords {

    private final Map<String, Animal> animals = new LinkedHashMap<>();
    private final Map<String, Map<String, Vaccine>> vaccines = new HashMap<>();
    private final Map<String, Instant> stamps = new HashMap<>();
    private Instant serverTime = Instant.parse("2026-01-01T00:00:00Z");

    /** How many animals each fetch returned, oldest first: what a pull cost. */
    final List<Integer> fetchSizes = new ArrayList<>();
    int fullFetches;
    boolean failNextPush;

    @Override
    public RemoteChanges fetchAll() {
        fullFetches++;
        return fetch(null);
    }

    @Override
    public RemoteChanges fetchChangedSince(Instant since) {
        return fetch(since);
    }

    private RemoteChanges fetch(Instant since) {
        List<RemoteAnimal> result = new ArrayList<>();
        for (Animal animal : animals.values()) {
            Instant stamp = stamps.get(animal.getRecordNumber());
            if (since != null && (stamp == null || !stamp.isAfter(since))) {
                continue;
            }
            List<Vaccine> own = vaccines.getOrDefault(animal.getRecordNumber(), Map.of()).values().stream()
                    .map(InMemoryRemoteRecords::copy)
                    .toList();
            result.add(new RemoteAnimal(copy(animal), own));
        }
        fetchSizes.add(result.size());
        return new RemoteChanges(result, serverTime);
    }

    @Override
    public void push(List<Animal> pushedAnimals, List<Vaccine> pushedVaccines, Map<String, String> deletedVaccines)
            throws Exception {
        if (failNextPush) {
            failNextPush = false;
            throw new IllegalStateException("network dropped");
        }
        Instant stamp = tick(Duration.ofSeconds(1));
        for (Animal animal : pushedAnimals) {
            animals.put(animal.getRecordNumber(), copy(animal));
            stamps.put(animal.getRecordNumber(), stamp);
        }
        for (Vaccine vaccine : pushedVaccines) {
            vaccines.computeIfAbsent(vaccine.getAnimalRecordNumber(), k -> new LinkedHashMap<>())
                    .put(vaccine.getId(), copy(vaccine));
            stamps.put(vaccine.getAnimalRecordNumber(), stamp);
        }
        deletedVaccines.forEach((vaccineId, animalRecordNumber) -> {
            vaccines.getOrDefault(animalRecordNumber, new HashMap<>()).remove(vaccineId);
            stamps.put(animalRecordNumber, stamp);
        });
    }

    /** Moves the server's clock forward and returns the new time. */
    Instant tick(Duration elapsed) {
        serverTime = serverTime.plus(elapsed);
        return serverTime;
    }

    /** A change made outside the application, such as an edit in the Firebase console: no stamp. */
    void editWithoutStamp(Animal animal) {
        animals.put(animal.getRecordNumber(), copy(animal));
    }

    /** A write the server received at {@code receivedAt} that only now becomes visible. */
    void landLate(Animal animal, Instant receivedAt) {
        animals.put(animal.getRecordNumber(), copy(animal));
        stamps.put(animal.getRecordNumber(), receivedAt);
    }

    Instant now() {
        return serverTime;
    }

    private static Animal copy(Animal source) {
        Animal animal = Animal.fromExistingRecord(source.getRecordNumber());
        animal.setChipNumber(source.getChipNumber());
        animal.setBarcode(source.getBarcode());
        animal.setAdmissionDate(source.getAdmissionDate());
        animal.setCollectedBy(source.getCollectedBy());
        animal.setPlaceId(source.getPlaceId());
        animal.setReasonForRescue(source.getReasonForRescue());
        animal.setSpecies(source.getSpecies());
        animal.setApproximateAge(source.getApproximateAge());
        animal.setSex(source.getSex());
        animal.setName(source.getName());
        animal.setAilments(source.getAilments());
        animal.setNeuteringDate(source.getNeuteringDate());
        animal.setAdopted(source.isAdopted());
        animal.setActive(source.isActive());
        animal.setSynced(source.isSynced());
        animal.setLastModified(source.getLastModified());
        return animal;
    }

    private static Vaccine copy(Vaccine source) {
        Vaccine vaccine = Vaccine.fromExistingRecord(source.getId());
        vaccine.setAnimalRecordNumber(source.getAnimalRecordNumber());
        vaccine.setVaccineName(source.getVaccineName());
        vaccine.setVaccinationDate(source.getVaccinationDate());
        vaccine.setSynced(source.isSynced());
        vaccine.setLastModified(source.getLastModified());
        return vaccine;
    }
}
