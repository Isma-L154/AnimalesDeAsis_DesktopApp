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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setUpDatePickerFormat();
    }

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

    public void setAnimalInfo(String name, String recordNumber) {
        this.animalName = name;
        this.animalRecordNumber = recordNumber;
        animalInfoLabel.setText("Animal: " + name);
    }

    /**
     * Here we set the callback that will be called when a new vaccine is created
     * */
    public void setOnVaccineCreated(Consumer<Vaccine> callback) {
        this.onVaccineCreated = callback;
    }

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
