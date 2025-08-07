package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Service.Animal.AnimalService;
import com.asosiaciondeasis.animalesdeasis.Service.Place.PlaceService;
import com.asosiaciondeasis.animalesdeasis.Util.BarcodeScannerUtil;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class EditAnimalController implements IPortalAwareController {

    private final AnimalService animalService = ServiceFactory.getAnimalService();
    private final PlaceService placeService = ServiceFactory.getPlaceService();
    private final BarcodeScannerUtil scannerUtil = new BarcodeScannerUtil();

    @FXML private TextField nameField;
    @FXML private ComboBox<String> speciesComboBox;
    @FXML private ComboBox<String> sexComboBox;
    @FXML private Spinner<Integer> ageSpinner;
    @FXML private ComboBox<String> placeComboBox;
    @FXML private TextField collectedByField;
    @FXML private DatePicker admissionDatePicker;
    @FXML private DatePicker neuteringDatePicker;
    @FXML private TextField chipNumberField;
    @FXML private TextArea rescueReasonArea;
    @FXML private TextArea ailmentsArea;
    @FXML private CheckBox adoptedCheckBox;
    @FXML private Button updateButton;
    @FXML private Button scanChipButton;
    @FXML private StackPane rootPane;

    private final String scannedChipNumber = null;
    private Animal currentAnimal;
    private PortalController portalController;
    private List<Place> allPlaces;

    @FXML
    public void initialize() {
        configureFields();
        configureDatePickers();
        loadPlaces();
        setupPlaceFiltering();
    }

    private void configureFields() {
        speciesComboBox.setItems(FXCollections.observableArrayList("Perro", "Gato"));
        sexComboBox.setItems(FXCollections.observableArrayList("Macho", "Hembra"));
        SpinnerValueFactory<Integer> ageFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 0);
        ageSpinner.setValueFactory(ageFactory);
    }

    private void configureDatePickers() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        StringConverter<LocalDate> converter = new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? date.format(formatter) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                return string.isEmpty() ? null : LocalDate.parse(string, formatter);
            }
        };

        admissionDatePicker.setConverter(converter);
        neuteringDatePicker.setConverter(converter);
        admissionDatePicker.setPromptText("dd-MM-yyyy");
        neuteringDatePicker.setPromptText("dd-MM-yyyy");
    }

    private void loadPlaces() {
        allPlaces = placeService.getAllPlaces();
    }

    private void setupPlaceFiltering() {
        ObservableList<String> placeNames = FXCollections.observableArrayList();
        for (Place place : allPlaces) {
            placeNames.add(place.getName());
        }
        placeComboBox.setItems(placeNames);
    }

    public void setAnimalData(Animal animal) {
        this.currentAnimal = animal;

        nameField.setText(animal.getName());
        speciesComboBox.setValue(animal.getSpecies());
        sexComboBox.setValue(animal.getSex());
        ageSpinner.getValueFactory().setValue(animal.getApproximateAge());
        collectedByField.setText(animal.getCollectedBy());
        adoptedCheckBox.setSelected(animal.isAdopted());
        admissionDatePicker.setValue(DateUtils.parseIsoToLocalDate(animal.getAdmissionDate()));
        neuteringDatePicker.setValue(DateUtils.parseIsoToLocalDate(animal.getNeuteringDate()));

        if (animal.getChipNumber() != null && !animal.getChipNumber().isBlank()) {
            chipNumberField.setText(animal.getChipNumber());
        } else if (animal.getBarcode() != null && !animal.getBarcode().isBlank()) {
            chipNumberField.setText(animal.getBarcode());
        } else {
            chipNumberField.setText("");
        }
        rescueReasonArea.setText(animal.getReasonForRescue());
        ailmentsArea.setText(animal.getAilments());

        //TODO Replace with a more robust way to handle places, also put an if to check if the place is null
        Place place = allPlaces.stream()
                .filter(p -> Objects.equals(p.getId(), animal.getPlaceId()))
                .findFirst()
                .orElse(null);
        if (place != null) placeComboBox.setValue(place.getName());
    }

    @FXML
    public void handleScanBarcode() {
        scannerUtil.startScanning(code -> Platform.runLater(() -> chipNumberField.setText(code)));
    }

    @FXML
    public void handleUpdate() throws Exception {
        if (!validateInputs()) return;

        currentAnimal.setName(nameField.getText().trim());
        currentAnimal.setSpecies(speciesComboBox.getValue());
        currentAnimal.setSex(sexComboBox.getValue());
        currentAnimal.setApproximateAge(ageSpinner.getValue());
        currentAnimal.setCollectedBy(collectedByField.getText().trim());
        currentAnimal.setAdmissionDate(DateUtils.convertToIsoFormat(admissionDatePicker.getValue()));
        currentAnimal.setAdopted(adoptedCheckBox.isSelected());
        if (neuteringDatePicker.getValue() != null) {
            currentAnimal.setNeuteringDate(DateUtils.convertToIsoFormat(neuteringDatePicker.getValue()));
        } else {
            currentAnimal.setNeuteringDate(null);
        }

        String chip = (scannedChipNumber != null && !scannedChipNumber.isBlank())
                ? scannedChipNumber.trim()
                : chipNumberField.getText();

        currentAnimal.setChipNumber(chip != null ? chip.trim() : null);
        currentAnimal.setBarcode(null);
        currentAnimal.setReasonForRescue(rescueReasonArea.getText().trim());
        currentAnimal.setAilments(ailmentsArea.getText().trim());

        Place selectedPlace = getSelectedPlace();
        if (selectedPlace != null) {
            currentAnimal.setPlaceId(selectedPlace.getId());
        }
        currentAnimal.setSynced(false);
        boolean updated = animalService.updateAnimal(currentAnimal);
        if (updated) {
            NavigationHelper.showInfoAlert("Exito", "Animal actualizado exitosamente.");
            NavigationHelper.goToAnimalModule(portalController);
        } else {
            NavigationHelper.showErrorAlert("Error", null, "Ocurrió un error al actualizar el animal.");
        }
    }

    private Place getSelectedPlace() {
        String selectedName = placeComboBox.getValue();
        return allPlaces.stream()
                .filter(p -> p.getName().equals(selectedName))
                .findFirst()
                .orElse(null);
    }

    private boolean validateInputs() {
        String name = nameField.getText().trim();
        String collectedBy = collectedByField.getText().trim();

        Pattern noSpecialChars = Pattern.compile("^[a-zA-Z0-9\\s]+$");

        if (!noSpecialChars.matcher(name).matches()) {
            NavigationHelper.showErrorAlert("Error", null, "El nombre no debe contener caracteres especiales.");
            return false;
        }

        if (speciesComboBox.getValue() == null) {
            NavigationHelper.showErrorAlert("Error", null, "Debe seleccionar una especie.");
            return false;
        }

        if (sexComboBox.getValue() == null) {
            NavigationHelper.showErrorAlert("Error", null, "Debe seleccionar el sexo.");
            return false;
        }

        if (ageSpinner.getValue() == null || ageSpinner.getValue() <= 0) {
            NavigationHelper.showErrorAlert("Error", null, "Debe ingresar una edad válida.");
            return false;
        }

        if (admissionDatePicker.getValue() == null) {
            NavigationHelper.showErrorAlert("Error", null, "Debe seleccionar la fecha de ingreso.");
            return false;
        }

        if (getSelectedPlace() == null) {
            NavigationHelper.showErrorAlert("Error", null, "Debe seleccionar un lugar.");
            return false;
        }

        if (collectedBy.isEmpty()) {
            NavigationHelper.showErrorAlert("Error", null, "Debe indicar quién recogió al animal.");
            return false;
        }
        return true;
    }

    @Override
    public void setPortalController(PortalController controller) {
        this.portalController = controller;
    }

    @FXML
    public void goToAnimalModule() {
        NavigationHelper.goToAnimalModule(portalController);
    }
}
