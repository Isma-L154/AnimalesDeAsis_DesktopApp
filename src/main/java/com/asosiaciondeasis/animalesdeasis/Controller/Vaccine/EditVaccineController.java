package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.FieldValidation;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.function.Consumer;


public class EditVaccineController implements Initializable {

    @FXML private TextField vaccineNameField;
    @FXML private Label animalInfoLabel;
    @FXML private DatePicker vaccinationDatePicker;

    private final FieldValidation validation = new FieldValidation();

    private Animal currentAnimal;
    private String animalName;
    private Vaccine currentVaccine;
    private Consumer<Vaccine> onVaccineUpdated;

    /**
     * Initializes the controller and sets up the date picker format.
     * Called automatically after FXML fields are injected.
     *
     * @param url The location used to resolve relative paths for the root object, or null if unknown.
     * @param resourceBundle The resources used to localize the root object, or null if not localized.
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupDatePickerFormat();
    }

    /**
     * Configures the DatePicker to use the "dd-MM-yyyy" format and sets the prompt text.
     */
    private void setupDatePickerFormat() {

        vaccinationDatePicker.setPromptText("dd-mm-yyyy");
        vaccinationDatePicker.setConverter(new javafx.util.StringConverter<LocalDate>() {
            private final java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

            @Override
            public String toString(LocalDate date) {
                return (date != null) ? formatter.format(date) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                return (string != null && !string.isEmpty()) ?
                        LocalDate.parse(string, formatter) : null;
            }
        });
    }
    /**
     * Sets the callback to be invoked when a vaccine is updated.
     *
     * @param callback The Consumer that will handle the updated Vaccine object.
     */
    public void setOnVaccineUpdated(Consumer<Vaccine> callback) {
        this.onVaccineUpdated = callback;
    }

    /**
     * Sets the animal information (name) to be displayed in the form.
     *
     * @param name The name of the animal.
     */
    public void setAnimalInfo(String name) {
        this.animalName = name;
        animalInfoLabel.setText("Animal: " + name);
    }

    /**
     * Populates the form fields with the data of the vaccine to be edited.
     * Sets the vaccine name and vaccination date in the form.
     *
     * @param vaccine The Vaccine object whose data will be loaded into the form.
     */
    public void setVaccineData(Vaccine vaccine) {
        this.currentVaccine = vaccine;
        if (vaccine != null) {
            vaccineNameField.setText(vaccine.getVaccineName());

            // Convert the vaccination date from ISO format to LocalDate for the DatePicker
            if (vaccine.getVaccinationDate() != null && !vaccine.getVaccinationDate().isEmpty()) {
                LocalDate date = DateUtils.utcStringToLocalDate(vaccine.getVaccinationDate());
                vaccinationDatePicker.setValue(date);
            }
        }
    }

    /**
     * Handles the update action when the user submits the form.
     * Validates the form, updates the Vaccine object, invokes the callback, and closes the window.
     */
    @FXML
    private void onUpdateAction() {
        if (validateForm()) {
            try {

                String vaccineName = vaccineNameField.getText().trim();
                LocalDate selectedDate = vaccinationDatePicker.getValue();
                String utcDate = DateUtils.localDateToUtcString(selectedDate);

                currentVaccine.setVaccineName(vaccineName);
                currentVaccine.setVaccinationDate(utcDate);
                currentVaccine.setSynced(false);

                if (onVaccineUpdated != null) {
                    onVaccineUpdated.accept(currentVaccine);
                }

                onCancelAction();

            } catch (Exception e) {
                NavigationHelper.showErrorAlert("Error", "Error al actualizar la vacuna", e.getMessage());
            }
        }
    }

    /**
     * Checks the form and marks whatever is wrong, in place.
     *
     * <p>This used to gather every problem into a string and raise a dialog
     * listing them. The dialog could say what was wrong but never <em>where</em>:
     * you read it, dismissed it, and then went looking. Each message now sits
     * under the field that caused it, and focus lands on the first one.</p>
     *
     * @return true if the form is valid
     */
    private boolean validateForm() {
        validation.clear();
        boolean valid = true;

        String name = vaccineNameField.getText();
        if (name == null || name.trim().isEmpty()) {
            validation.reject(vaccineNameField, "El nombre de la vacuna es obligatorio");
            valid = false;
        } else if (name.trim().length() > 100) {
            validation.reject(vaccineNameField,
                    "No puede exceder 100 caracteres (van " + name.trim().length() + ")");
            valid = false;
        }

        LocalDate date = vaccinationDatePicker.getValue();
        if (date == null) {
            validation.reject(vaccinationDatePicker, "La fecha de vacunación es obligatoria");
            valid = false;
        } else if (date.isAfter(LocalDate.now())) {
            validation.reject(vaccinationDatePicker, "La fecha no puede ser futura");
            valid = false;
        }

        if (!valid) {
            validation.focusFirstError();
        }
        return valid;
    }

    @FXML
    public void onCancelAction() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) animalInfoLabel.getScene().getWindow();
        stage.close();
    }

}
