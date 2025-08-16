package com.asosiaciondeasis.animalesdeasis.Controller.Animal;


import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Controller.Vaccine.VaccineManagementController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class DetailAnimalController implements IPortalAwareController {

    @FXML private Label nameLabel;
    @FXML private Label speciesLabel;
    @FXML private Label ageLabel;
    @FXML private Label sexLabel;
    @FXML private Label chipNumberLabel;
    @FXML private Label barcodeLabel;
    @FXML private Label admissionDateLabel;
    @FXML private Label neuteringDateLabel;
    @FXML private Label collectedByLabel;
    @FXML private Label placeProvinceLabel;
    @FXML private Label rescueReasonLabel;
    @FXML private Label ailmentsLabel;
    @FXML private Button editButton;

    private List<Place> allPlaces;
    private Animal currentAnimal;
    private PortalController portalController;

    public void setAnimalDetails(Animal animal, List<Place> allPlaces) {
        this.currentAnimal = animal;
        this.allPlaces = allPlaces;
        String admissionDateForm = DateUtils.parseIsoToLocalDate(animal.getAdmissionDate()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String neuteringDateForm = "Sin información";
        if (animal.getNeuteringDate() != null) {
            try {
                neuteringDateForm = DateUtils.parseIsoToLocalDate(animal.getNeuteringDate())
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception e) {
                neuteringDateForm = "Fecha inválida";
            }
        }

        nameLabel.setText(validate(animal.getName()));
        speciesLabel.setText(validate(animal.getSpecies()));
        int age = animal.getApproximateAge();
        if (age == 1) {
            ageLabel.setText(age + " año");
        } else {
            ageLabel.setText(age + " años");
        }
        sexLabel.setText(validate(animal.getSex()));
        chipNumberLabel.setText(validate(animal.getChipNumber()));
        admissionDateLabel.setText(validate(admissionDateForm));
        neuteringDateLabel.setText(neuteringDateForm); //We don't validate here because it has its own logic up there
        collectedByLabel.setText(validate(animal.getCollectedBy()));
        rescueReasonLabel.setText(validate(animal.getReasonForRescue()));
        ailmentsLabel.setText(validate(animal.getAilments()));

        Place place = allPlaces.stream()
                .filter(p -> p.getId() == animal.getPlaceId())
                .findFirst()
                .orElse(null);

        if (place != null) {
            placeProvinceLabel.setText(place.getName() + ", " + place.getProvinceName());
        } else {
            placeProvinceLabel.setText("Sin información");
        }
        updateEditButtonVisibility();
    }

    @FXML
    public void goToEditModule() {
        if (portalController != null && currentAnimal != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Animal/EditAnimal.fxml"));
                Parent root = loader.load();

                EditAnimalController editController = loader.getController();
                editController.setPortalController(portalController);
                editController.setAnimalData(currentAnimal);

                portalController.setContent(root);
            } catch (Exception e) {
                NavigationHelper.showErrorAlert("Error", "No se pudo cargar el formulario de edición", e.getMessage());
            }
        } else {
            NavigationHelper.showErrorAlert("Error", null, "No se pudo obtener el animal para editar.");
        }
    }

    @FXML
    public void goToVaccineManagement() {
        if (portalController != null && currentAnimal != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Vaccine/VaccineManagement.fxml"));
                Parent root = loader.load();

                VaccineManagementController vaccineController = loader.getController();
                vaccineController.setPortalController(portalController);
                vaccineController.setCurrentAnimal(currentAnimal);

                portalController.setContent(root);
            } catch (Exception e) {
                NavigationHelper.showErrorAlert("Error", "No se pudo cargar el módulo de vacunas", e.getMessage());
            }
        } else {
            NavigationHelper.showErrorAlert("Error", null, "No se pudo obtener el animal para gestionar las vacunas.");
        }
    }

    private void updateEditButtonVisibility() {
        try {
            Animal currentAnimalFromDB = ServiceFactory.getAnimalService().findByRecordNumber(currentAnimal.getRecordNumber());

            if (currentAnimalFromDB != null) {
                boolean isActive = currentAnimalFromDB.isActive();
                editButton.setVisible(isActive);
                editButton.setManaged(isActive);
            }
        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", "No se pudo verificar el estado del animal", e.getMessage());
        }
    }
    private String validate(String value) {
        return (value == null || value.isEmpty()) ? "Sin información" : value;
    }

    @Override
    public void setPortalController(PortalController controller) {
        this.portalController = controller;
    }
    public void goToAnimalModule() {
        NavigationHelper.goToAnimalModule(portalController);
    }

}
