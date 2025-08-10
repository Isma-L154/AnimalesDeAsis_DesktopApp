package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class VaccineManagementController implements IPortalAwareController {

    @FXML private Label animalInfoLabel;
    @FXML private TableView<Vaccine> vaccineTable;
    @FXML private TableColumn<Vaccine, String> vaccineNameColumn;
    @FXML private TableColumn<Vaccine, String> vaccinationDateColumn;
    @FXML private Label totalVaccinesLabel;
    @FXML private Label lastVaccineLabel;

    private PortalController portalController;
    private Animal currentAnimal;


    public void setCurrentAnimal(Animal animal) throws Exception {
        this.currentAnimal = animal;
        updateAnimalInfoLabel();
        loadVaccinesForAnimal();
    }

    private void updateAnimalInfoLabel() {
        if (animalInfoLabel != null && currentAnimal != null) {
            animalInfoLabel.setText("Animal: " + currentAnimal.getName());
        }
    }

    private void loadVaccinesForAnimal() throws Exception {
        if (currentAnimal == null) return;

        List<Vaccine> vaccines = ServiceFactory.getVaccineService().getVaccinesByAnimal(currentAnimal.getRecordNumber());

        ObservableList<Vaccine> observableVaccines = FXCollections.observableArrayList(vaccines);
        vaccineTable.setItems(observableVaccines);

        updateSummary(vaccines);

        if (vaccineNameColumn.getCellValueFactory() == null) {
            vaccineNameColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getVaccineName())
            );
        }

        if (vaccinationDateColumn.getCellValueFactory() == null) {
            vaccinationDateColumn.setCellValueFactory(cellData -> {
                String isoDateStr = cellData.getValue().getVaccinationDate();
                LocalDate date = DateUtils.parseIsoToLocalDate(isoDateStr);
                String formattedDate = (date != null) ? date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "N/A";
                return new SimpleStringProperty(formattedDate);
            });
        }
    }

    private void updateSummary(List<Vaccine> vaccines) {
        totalVaccinesLabel.setText(String.valueOf(vaccines.size()));

        if (!vaccines.isEmpty()) {
            LocalDate lastDate = vaccines.stream()
                    .map(vaccine -> DateUtils.parseIsoToLocalDate(vaccine.getVaccinationDate()))
                    .filter(date -> date != null)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            String formattedLastDate = (lastDate != null)
                    ? lastDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    : "N/A";

            lastVaccineLabel.setText(formattedLastDate);
        } else {
            lastVaccineLabel.setText("N/A");
        }
    }


    @Override
    public void setPortalController(PortalController controller) {this.portalController = controller;}
}
