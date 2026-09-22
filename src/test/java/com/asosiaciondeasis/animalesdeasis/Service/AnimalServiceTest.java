package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Service.Animal.AnimalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
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

        service.registerAnimal(animal);
        verify(animalDAO).insertAnimal(animal);
    }

    @Test
    void registerAnimalPropagatesDaoFailure() throws Exception {
        AnimalService service = new AnimalService(animalDAO);
        Animal animal = Animal.createNew();
        doThrow(new SQLException("disk I/O error")).when(animalDAO).insertAnimal(animal);

        assertThrows(SQLException.class, () -> service.registerAnimal(animal));
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

        // UI-driven updates must refresh last_modified so the change is picked
        // up by the next sync. Only the sync itself writes a remote timestamp.
        service.updateAnimal(animal);

        verify(animalDAO).updateAnimal(animal);
    }

    @Test
    void deleteAnimalDelegatesToDao() throws Exception {
        AnimalService service = new AnimalService(animalDAO);

        service.deleteAnimal("rec-1");

        verify(animalDAO).deleteAnimal("rec-1");
    }
}
