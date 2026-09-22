package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

public class EditAnimalController extends AnimalFormController {

    @FXML private CheckBox adoptedCheckBox;

    private Animal currentAnimal;
    /** The chip field's value when the form opened, to tell a manual edit from no change. */
    private String originalChipValue;

    public void setAnimalData(Animal animal) {
        this.currentAnimal = animal;
        readCommonFields(animal);
        adoptedCheckBox.setSelected(animal.isAdopted());

        originalChipValue = isPresent(animal.getChipNumber()) ? animal.getChipNumber()
                : isPresent(animal.getBarcode()) ? animal.getBarcode()
                : "";
        chipNumberField.setText(originalChipValue);
    }

    @FXML
    public void handleUpdate() {
        if (!validateInputs()) {
            return;
        }

        writeCommonFields(currentAnimal);

        // An adopted animal leaves the active list.
        boolean adopted = adoptedCheckBox.isSelected();
        currentAnimal.setAdopted(adopted);
        currentAnimal.setActive(!adopted);

        String fieldValue = text(chipNumberField);
        if (isPresent(scannedChipNumber)) {
            currentAnimal.setBarcode(scannedChipNumber.trim());
            currentAnimal.setChipNumber(scannedChipNumber.trim());
        } else {
            currentAnimal.setChipNumber(fieldValue.isEmpty() ? null : fieldValue);
            if (!fieldValue.equals(originalChipValue)) {
                // Typed over, so the old barcode no longer describes this animal.
                currentAnimal.setBarcode(null);
            }
        }
        currentAnimal.setSynced(false);

        persist(() -> ServiceFactory.getAnimalService().updateAnimal(currentAnimal),
                "Animal actualizado exitosamente.");
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
