package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import javafx.fxml.FXML;

import java.time.LocalDate;

public class CreateAnimalController extends AnimalFormController {

    @FXML
    @Override
    public void initialize() {
        super.initialize();
        admissionDatePicker.setValue(LocalDate.now());
    }

    @FXML
    public void handleSave() {
        if (!validateInputs()) {
            return;
        }

        Animal animal = Animal.createNew();
        writeCommonFields(animal);

        if (scannedChipNumber != null && !scannedChipNumber.isBlank()) {
            // A scanned code identifies the animal both ways.
            animal.setBarcode(scannedChipNumber.trim());
            animal.setChipNumber(scannedChipNumber.trim());
        } else {
            String manualChip = text(chipNumberField);
            animal.setChipNumber(manualChip.isEmpty() ? null : manualChip);
        }
        animal.setAdopted(false);
        animal.setSynced(false);

        persist(() -> ServiceFactory.getAnimalService().registerAnimal(animal),
                "Animal ingresado exitosamente.");
    }
}
