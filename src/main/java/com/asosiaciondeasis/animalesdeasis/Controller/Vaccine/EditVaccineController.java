package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.FieldValidation;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class EditVaccineController {

    @FXML private TextField vaccineNameField;
    @FXML private Label animalInfoLabel;
    @FXML private DatePicker vaccinationDatePicker;

    private final FieldValidation validation = new FieldValidation();

    private Vaccine currentVaccine;
    private Consumer<Vaccine> onVaccineUpdated;

    @FXML
    public void initialize() {
        VaccineForm.configureDatePicker(vaccinationDatePicker);
    }

    /** Receives the edited vaccine when the form is submitted; the caller persists it. */
    public void setOnVaccineUpdated(Consumer<Vaccine> callback) {
        this.onVaccineUpdated = callback;
    }

    public void setAnimalInfo(String name) {
        animalInfoLabel.setText("Animal: " + name);
    }

    public void setVaccineData(Vaccine vaccine) {
        this.currentVaccine = vaccine;
        vaccineNameField.setText(vaccine.getVaccineName());
        vaccinationDatePicker.setValue(DateUtils.utcStringToLocalDate(vaccine.getVaccinationDate()));
    }

    @FXML
    private void onUpdateAction() {
        if (!VaccineForm.validate(validation, vaccineNameField, vaccinationDatePicker)) {
            return;
        }

        currentVaccine.setVaccineName(vaccineNameField.getText().trim());
        currentVaccine.setVaccinationDate(DateUtils.localDateToUtcString(vaccinationDatePicker.getValue()));
        currentVaccine.setSynced(false);

        if (onVaccineUpdated != null) {
            onVaccineUpdated.accept(currentVaccine);
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
