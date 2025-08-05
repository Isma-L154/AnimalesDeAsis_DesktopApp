package com.asosiaciondeasis.animalesdeasis.Controller.Animal;


import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Util.NavigationHelper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

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

    private List<Place> allPlaces;
    private PortalController portalController;

    public void setAnimalDetails(Animal animal, List<Place> allPlaces) {
        this.allPlaces = allPlaces;

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
        barcodeLabel.setText(validate(animal.getBarcode()));
        admissionDateLabel.setText(validate(animal.getAdmissionDate()));
        neuteringDateLabel.setText(validate(animal.getNeuteringDate()));
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
    }
    private String validate(String value) {
        return (value == null || value.isEmpty()) ? "Sin información" : value;
    }
    @Override
    public void setPortalController(PortalController controller) {this.portalController = controller;}
    public void goToAnimalModule() { NavigationHelper.goToAnimalModule(portalController);}
}
