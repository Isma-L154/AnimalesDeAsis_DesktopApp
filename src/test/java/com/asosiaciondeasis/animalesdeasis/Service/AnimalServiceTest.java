package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Service.Animal.AnimalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private IAnimalDAO animalDAO;

    @Test
    void registerAnimalDelegatesToDao() throws Exception {
        AnimalService service = new AnimalService(animalDAO);
        Animal animal = Animal.createNew();

        assertTrue(service.registerAnimal(animal));
        verify(animalDAO).insertAnimal(animal);
    }

    @Test
    void getActiveAnimalsDelegatesToDao() throws Exception {
        AnimalService service = new AnimalService(animalDAO);
        Animal animal = Animal.createNew();
        when(animalDAO.getAllAnimals()).thenReturn(List.of(animal));

        assertEquals(1, service.getActiveAnimals().size());
        verify(animalDAO).getAllAnimals();
    }

    @Test
    void updateAnimalAlwaysBumpsTimestamp() throws Exception {
        AnimalService service = new AnimalService(animalDAO);
        Animal animal = Animal.createNew();

        // Even when the caller passes false, UI-driven updates must refresh the
        // last_modified timestamp so the change is picked up by the next sync.
        service.updateAnimal(animal, false);

        verify(animalDAO).updateAnimal(animal, true);
    }

    @Test
    void deleteAnimalDelegatesToDao() throws Exception {
        AnimalService service = new AnimalService(animalDAO);

        service.deleteAnimal("rec-1");

        verify(animalDAO).deleteAnimal("rec-1");
    }
}
