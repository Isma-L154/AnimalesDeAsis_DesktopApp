package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class CreateVaccineController implements Initializable{

    @FXML private Label animalInfoLabel;
    @FXML private TextField vaccineNameField;
    @FXML private DatePicker vaccinationDatePicker;

    private String animalRecordNumber;
    private String animalName;
    private Consumer<Vaccine> onVaccineCreated;

    /**
     * Initializes the controller and sets up the date picker format.
     * Called automatically after FXML fields are injected.
     *
     * @param location The location used to resolve relative paths for the root object, or null if unknown.
     * @param resources The resources used to localize the root object, or null if not localized.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setUpDatePickerFormat();
    }

    /**
     * Configures the DatePicker to use the "dd-MM-yyyy" format and sets the default value to today.
     */
    private void setUpDatePickerFormat() {
        // DatePicker is going to be in format dd-MM-yyyy, in here we set the converter
        vaccinationDatePicker.setConverter(new StringConverter<LocalDate>() {
            private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

            @Override
            public String toString(LocalDate date) {
                return (date != null) ? dateFormatter.format(date) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isEmpty()) {
                    try {
                        return LocalDate.parse(string, dateFormatter);
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }
        });
        //Set the default date to today
        vaccinationDatePicker.setValue(LocalDate.now());
    }

    /**
     * Sets the animal information (name and record number) to be displayed in the form.
     *
     * @param name The name of the animal.
     * @param recordNumber The record number of the animal.
     */
    public void setAnimalInfo(String name, String recordNumber) {
        this.animalName = name;
        this.animalRecordNumber = recordNumber;
        animalInfoLabel.setText("Animal: " + name);
    }

    /**
     * Sets the callback to be invoked when a new vaccine is created.
     *
     * @param callback The Consumer that will handle the created Vaccine object.
     */
    public void setOnVaccineCreated(Consumer<Vaccine> callback) {
        this.onVaccineCreated = callback;
    }

    /**
     * Handles the save action when the user submits the form.
     * Validates the form, creates a new Vaccine object, invokes the callback, and closes the window.
     */
    @FXML
    public void onSaveAction() {
        if (!validateForm()) return;

        Vaccine newVaccine = Vaccine.createNew();


        newVaccine.setAnimalRecordNumber(animalRecordNumber);
        newVaccine.setVaccineName(vaccineNameField.getText().trim());
        newVaccine.setVaccinationDate(DateUtils.localDateToUtcString(vaccinationDatePicker.getValue()));
        newVaccine.setSynced(false);

        if (onVaccineCreated != null) {
            onVaccineCreated.accept(newVaccine);
        }
        closeWindow();
    }



    @FXML
    public void onCancelAction() {closeWindow();}

    /**
     * Validates the form fields for vaccine name and vaccination date.
     * Shows an error alert if validation fails.
     *
     * @return true if the form is valid, false otherwise.
     */
    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        if (vaccineNameField.getText() == null || vaccineNameField.getText().trim().isEmpty()) {
            errors.append("- El nombre de la vacuna es obligatorio\n");
        } else if (vaccineNameField.getText().trim().length() > 100) {
            errors.append("- El nombre de la vacuna no puede exceder 100 caracteres\n");
        }

        if (vaccinationDatePicker.getValue() == null) {
            errors.append("- La fecha de vacunación es obligatoria\n");
        } else if (vaccinationDatePicker.getValue().isAfter(LocalDate.now())) {
            errors.append("- La fecha de vacunación no puede ser futura\n");
        }

        if (errors.length() > 0) {
            NavigationHelper.showErrorAlert("Datos inválidos",
                    "Por favor corrija los siguientes errores:", errors.toString());
            return false;
        }

        return true;
    }

    private void closeWindow() {
        Stage stage = (Stage) animalInfoLabel.getScene().getWindow();
        stage.close();
    }
}
