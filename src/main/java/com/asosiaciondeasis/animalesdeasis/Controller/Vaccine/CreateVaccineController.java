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

    public void setAnimalInfo(String recordNumber, String name) {
        this.animalRecordNumber = recordNumber;
        this.animalName = name;
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
        if (!validateFields()) return;

        Vaccine newVaccine = new Vaccine();

        String vaccineName = vaccineNameField.getText().trim();

        //Set the date in ISO format, for the firebase and the database
        LocalDate vaccinationDate = vaccinationDatePicker.getValue();
        String isoDate = DateUtils.convertToIsoFormat(vaccinationDate);

        newVaccine.setAnimalRecordNumber(animalRecordNumber);
        newVaccine.setVaccineName(vaccineName);
        newVaccine.setVaccinationDate(isoDate);

        if (onVaccineCreated != null) {
            onVaccineCreated.accept(newVaccine);
        }
        closeWindow();
    }



    @FXML
    public void onCancelAction() {closeWindow();}

    private boolean validateFields(){
        if (vaccineNameField.getText() == null || vaccineNameField.getText().trim().isEmpty()) {
            NavigationHelper.showWarningAlert("Campo requerido", "El nombre de la vacuna es obligatorio.");
            vaccineNameField.requestFocus();
            return false;
        }

        if (vaccinationDatePicker.getValue() == null) {
            NavigationHelper.showWarningAlert("Campo requerido", "La fecha de vacunación es obligatoria.");
            vaccinationDatePicker.requestFocus();
            return false;
        }

        // In here we check if the date is in the future
        if (vaccinationDatePicker.getValue().isAfter(LocalDate.now())) {
            NavigationHelper.showWarningAlert("Fecha inválida", "La fecha de vacunación no puede ser futura.");
            vaccinationDatePicker.requestFocus();
            return false;
        }

        return true;
    }

    private void closeWindow() {
        Stage stage = (Stage) animalInfoLabel.getScene().getWindow();
        stage.close();
    }
}
