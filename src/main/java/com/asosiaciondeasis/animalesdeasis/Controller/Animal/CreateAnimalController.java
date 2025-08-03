package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Service.Animal.AnimalService;
import com.asosiaciondeasis.animalesdeasis.Service.Place.PlaceService;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.BarcodeScannerUtil;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

public class CreateAnimalController {

    @FXML private TextField nameField;
    @FXML private ComboBox<String> speciesComboBox;
    @FXML private ComboBox<String> sexComboBox;
    @FXML private Spinner<Integer> ageSpinner;
    @FXML private ComboBox<String> placeComboBox;
    @FXML private TextField collectedByField;
    @FXML private DatePicker admissionDatePicker;
    @FXML private DatePicker neuteringDatePicker;
    @FXML private TextField chipNumberField;
    @FXML private TextField barcodeField;
    @FXML private TextArea rescueReasonArea;
    @FXML private TextArea ailmentsArea;
    @FXML private CheckBox adoptedCheckBox;
    @FXML private Button scanBarcodeButton;
    @FXML private Button saveButton;
    @FXML private StackPane rootPane;

    private final AnimalService animalService = ServiceFactory.getAnimalService();
    private final PlaceService placeService = ServiceFactory.getPlaceService();
    private final BarcodeScannerUtil scannerUtil = new BarcodeScannerUtil();
    private List<Place> allPlaces;

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
        String selectedName = (String) placeComboBox.getValue();
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
            barcodeField.setText(code);
            chipNumberField.setText(code);
        });
    }

    @FXML
    public void handleSave() throws Exception {
        if (!validateInputs()) return;

        Animal animal = Animal.createNew();
        animal.setChipNumber(chipNumberField.getText().trim());
        animal.setBarcode(barcodeField.getText().trim());
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
        if (neuteringDatePicker.getValue() != null)
            animal.setNeuteringDate(DateUtils.convertToIsoFormat(neuteringDatePicker.getValue()));
        else
            animal.setNeuteringDate(null);
        animal.setAdopted(adoptedCheckBox.isSelected());
        animal.setSynced(false);

        boolean saved = animalService.registerAnimal(animal);
        if (saved) {
            showInfo("Animal registrado exitosamente.");
            goToAnimalModule();
        } else {
            showError("Ocurrió un error al guardar el animal.");
        }
    }

    private void goToAnimalModule() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AnimalModule.fxml"));
            Parent content = loader.load();
            StackPane.setAlignment(content, Pos.CENTER);
            rootPane.getChildren().setAll(content);
        } catch (IOException e) {
            e.printStackTrace();
            showError("No se pudo cargar el módulo de animales.");
        }
    }

    private boolean validateInputs() {
        String chip = chipNumberField.getText().trim();
        String collectedBy = collectedByField.getText().trim();
        String name = nameField.getText().trim();

        Pattern noSpecialChars = Pattern.compile("^[a-zA-Z0-9\\s]+$");

        if (chip.isEmpty() || collectedBy.isEmpty() || name.isEmpty()) {
            showError("Todos los campos obligatorios deben estar completos.");
            return false;
        }

        if (!noSpecialChars.matcher(name).matches()) {
            showError("El nombre no debe contener caracteres especiales.");
            return false;
        }

        if (getSelectedPlace() == null) {
            showError("Debe seleccionar un lugar.");
            return false;
        }

        return true;
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Información");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
