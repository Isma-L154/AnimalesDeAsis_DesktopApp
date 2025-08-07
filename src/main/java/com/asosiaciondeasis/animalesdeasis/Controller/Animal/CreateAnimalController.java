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
import java.util.regex.Pattern;

public class CreateAnimalController implements IPortalAwareController {

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
    @FXML private Button scanBarcodeButton;
    @FXML private Button saveButton;
    @FXML private StackPane rootPane;

    private CheckBox adoptedCheckBox;
    private String scannedChipNumber = null;
    private List<Place> allPlaces;
    private PortalController portalController;

    @FXML
    public void initialize() {
        configureFields();
        loadPlaces();
        setupPlaceFiltering();
        configureDatePickers();
    }

    private void configureFields() {
        speciesComboBox.setItems(FXCollections.observableArrayList("Perro", "Gato"));
        sexComboBox.setItems(FXCollections.observableArrayList("Macho", "Hembra"));
        SpinnerValueFactory<Integer> ageFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 0);
        ageSpinner.setValueFactory(ageFactory);
    }

    private void configureDatePickers() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");


        admissionDatePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? date.format(formatter) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                return string.isEmpty() ? null : LocalDate.parse(string, formatter);
            }
        });

        neuteringDatePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? date.format(formatter) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                return string.isEmpty() ? null : LocalDate.parse(string, formatter);
            }
        });

        admissionDatePicker.setPromptText("dd-MM-yyyy");
        neuteringDatePicker.setPromptText("dd-MM-yyyy");

        admissionDatePicker.setValue(LocalDate.now());
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
        placeComboBox.setEditable(false);
    }


    @FXML
    private Place getSelectedPlace() {
        String selectedName = placeComboBox.getValue();
        if (selectedName == null) return null;

        List<Place> allPlaces = placeService.getAllPlaces();
        return allPlaces.stream()
                .filter(p -> p.getName().equals(selectedName))
                .findFirst()
                .orElse(null);
    }

    @FXML
    public void handleScanBarcode() {
        scannerUtil.startScanning(code -> {
            this.scannedChipNumber = code;

            Platform.runLater(() -> {
                chipNumberField.setText(code);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Éxito");
                alert.setHeaderText(null);
                alert.setContentText("Código escaneado exitosamente.");
                alert.showAndWait();
            });
        });
    }

    @FXML
    public void handleSave() throws Exception {
        if (!validateInputs()) return;

        Animal animal = Animal.createNew();
        String chip = (scannedChipNumber != null && !scannedChipNumber.isBlank())
                ? scannedChipNumber.trim()
                : chipNumberField.getText().trim();

        if (chip != null && !chip.isBlank()) {
            animal.setChipNumber(chip);
        }

        animal.setAdmissionDate(DateUtils.convertToIsoFormat(admissionDatePicker.getValue()));
        animal.setCollectedBy(collectedByField.getText().trim());

        Place selectedPlace = getSelectedPlace();
        if (selectedPlace != null) {
            animal.setPlaceId(selectedPlace.getId());
        }
        animal.setReasonForRescue(rescueReasonArea.getText().trim());
        animal.setSpecies(speciesComboBox.getValue());
        animal.setApproximateAge(ageSpinner.getValue());
        animal.setSex(sexComboBox.getValue());
        animal.setName(nameField.getText().trim());
        animal.setAilments(ailmentsArea.getText().trim());

        if (neuteringDatePicker.getValue() != null) {
            animal.setNeuteringDate(DateUtils.convertToIsoFormat(neuteringDatePicker.getValue()));
        } else {
            animal.setNeuteringDate(null);
        }

        animal.setAdopted(false);
        animal.setSynced(false);

        boolean saved = animalService.registerAnimal(animal);
        if (saved) {
            NavigationHelper.showInfoAlert("Exito", "Animal actualizado exitosamente.");
            NavigationHelper.goToAnimalModule(portalController);
        } else {
            NavigationHelper.showErrorAlert("Error", null, "Ocurrió un error al actualizar el animal.");
        }
    }

    private boolean validateInputs() {
        String chip = chipNumberField.getText().trim();
        String collectedBy = collectedByField.getText().trim();
        String name = nameField.getText().trim();

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

    public void goToAnimalModule() {
        NavigationHelper.goToAnimalModule(portalController);
    }
}
