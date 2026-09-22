package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.FieldValidation;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.function.Consumer;

public class CreateVaccineController {

    @FXML private Label animalInfoLabel;
    @FXML private TextField vaccineNameField;
    @FXML private DatePicker vaccinationDatePicker;

    private final FieldValidation validation = new FieldValidation();

    private String animalRecordNumber;
    private Consumer<Vaccine> onVaccineCreated;

    @FXML
    public void initialize() {
        VaccineForm.configureDatePicker(vaccinationDatePicker);
        vaccinationDatePicker.setValue(LocalDate.now());
    }

    public void setAnimalInfo(String name, String recordNumber) {
        this.animalRecordNumber = recordNumber;
        animalInfoLabel.setText("Animal: " + name);
    }

    /** Receives the new vaccine when the form is submitted; the caller persists it. */
    public void setOnVaccineCreated(Consumer<Vaccine> callback) {
        this.onVaccineCreated = callback;
    }

    @FXML
    public void onSaveAction() {
        if (!VaccineForm.validate(validation, vaccineNameField, vaccinationDatePicker)) {
            return;
        }

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
    public void onCancelAction() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) animalInfoLabel.getScene().getWindow()).close();
    }
}
