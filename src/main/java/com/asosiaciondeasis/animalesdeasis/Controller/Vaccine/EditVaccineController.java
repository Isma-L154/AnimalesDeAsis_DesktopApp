package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
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

    private Animal currentAnimal;
    private String animalName;
    private Vaccine currentVaccine;
    private Consumer<Vaccine> onVaccineUpdated;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupDatePickerFormat();
    }

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
    public void setOnVaccineUpdated(Consumer<Vaccine> callback) {
        this.onVaccineUpdated = callback;
    }

    public void setAnimalInfo(String name) {
        this.animalName = name;
        animalInfoLabel.setText("Animal: " + name);
    }

    public void setVaccineData(Vaccine vaccine) {
        this.currentVaccine = vaccine;
        if (vaccine != null) {
            vaccineNameField.setText(vaccine.getVaccineName());

            // Convert the vaccination date from ISO format to LocalDate for the DatePicker
            if (vaccine.getVaccinationDate() != null && !vaccine.getVaccinationDate().isEmpty()) {
                LocalDate date = DateUtils.parseIsoToLocalDate(vaccine.getVaccinationDate());
                vaccinationDatePicker.setValue(date);
            }
        }
    }

    @FXML
    private void onUpdateAction() {
        if (validateForm()) {
            try {

                String vaccineName = vaccineNameField.getText().trim();
                LocalDate selectedDate = vaccinationDatePicker.getValue();
                String isoDate = DateUtils.convertToIsoFormat(selectedDate);

                currentVaccine.setVaccineName(vaccineName);
                currentVaccine.setVaccinationDate(isoDate);

                if (onVaccineUpdated != null) {
                    onVaccineUpdated.accept(currentVaccine);
                }

                onCancelAction();

            } catch (Exception e) {
                NavigationHelper.showErrorAlert("Error", "Error al actualizar la vacuna", e.getMessage());
            }
        }
    }

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

    @FXML
    public void onCancelAction() {closeWindow();}

    private void closeWindow() {
        Stage stage = (Stage) animalInfoLabel.getScene().getWindow();
        stage.close();
    }

}
