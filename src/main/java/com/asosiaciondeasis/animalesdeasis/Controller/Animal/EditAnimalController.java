package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
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

    private String scannedChipNumber = null;
    private Animal currentAnimal;
    private PortalController portalController;
    private List<Place> allPlaces;

    /**
     * Initializes the controller, configures form fields, date pickers, loads places, and sets up place filtering.
     * Called automatically after FXML injection.
     */
    @FXML
    public void initialize() {
        configureFields();
        configureDatePickers();
        loadPlaces();
        setupPlaceFiltering();
    }

    /**
     * Configures ComboBoxes, Spinner, and TextArea formatters for the animal editing form.
     * Sets up allowed values and input restrictions.
     */
    private void configureFields() {
        speciesComboBox.setItems(FXCollections.observableArrayList("Perro", "Gato"));
        sexComboBox.setItems(FXCollections.observableArrayList("Macho", "Hembra"));
        SpinnerValueFactory<Integer> ageFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 0);
        ageSpinner.setValueFactory(ageFactory);

        rescueReasonArea.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().length() > 300) {
                return null;
            }
            return change;
        }));

        ailmentsArea.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().length() > 500) {
                return null;
            }
            return change;
        }));
    }

    /**
     * Configures the DatePickers for admission and neutering dates.
     * Sets formatting, disables future dates, and sets prompt texts.
     */
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

        admissionDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #ffcccc;");
                    setTooltip(new Tooltip("No se pueden seleccionar fechas futuras"));
                }
            }
        });
    }

    /**
     * Loads all available places from the PlaceService for selection in the ComboBox.
     */
    private void loadPlaces() {
        allPlaces = placeService.getAllPlaces();
    }

    /**
     * Sets up the place ComboBox with the names of all available places.
     */
    private void setupPlaceFiltering() {
        ObservableList<String> placeNames = FXCollections.observableArrayList();
        for (Place place : allPlaces) {
            placeNames.add(place.getName());
        }
        placeComboBox.setItems(placeNames);
    }

    /**
     * Populates the form fields with the data of the animal to be edited.
     * Sets all fields and ComboBoxes to reflect the current animal's properties.
     *
     * @param animal The Animal object whose data will be loaded into the form.
     */
    public void setAnimalData(Animal animal) {
        this.currentAnimal = animal;

        nameField.setText(animal.getName());
        speciesComboBox.setValue(animal.getSpecies());
        sexComboBox.setValue(animal.getSex());
        ageSpinner.getValueFactory().setValue(animal.getApproximateAge());
        collectedByField.setText(animal.getCollectedBy());
        adoptedCheckBox.setSelected(animal.isAdopted());
        admissionDatePicker.setValue(DateUtils.utcStringToLocalDate(animal.getAdmissionDate()));
        neuteringDatePicker.setValue(DateUtils.utcStringToLocalDate(animal.getNeuteringDate()));

        if (animal.getChipNumber() != null && !animal.getChipNumber().isBlank()) {
            chipNumberField.setText(animal.getChipNumber());
        } else if (animal.getBarcode() != null && !animal.getBarcode().isBlank()) {
            chipNumberField.setText(animal.getBarcode());
        } else {
            chipNumberField.setText("");
        }
        rescueReasonArea.setText(animal.getReasonForRescue() != null ? animal.getReasonForRescue() : null);
        ailmentsArea.setText(animal.getAilments() != null ? animal.getAilments() : null);

        Place place = allPlaces.stream()
                .filter(p -> Objects.equals(p.getId(), animal.getPlaceId()))
                .findFirst()
                .orElse(null);
        if (place != null) placeComboBox.setValue(place.getName());
    }

    /**
     * Initiates the barcode scanning process and sets the scanned code in the chip number field.
     * Stores the scanned value for later use during update.
     */
    @FXML
    public void handleScanBarcode() {
        scannerUtil.startScanning(code -> {
            // Store the scanned code for later use in update
            this.scannedChipNumber = code;
            Platform.runLater(() -> {
                // Display the scanned code in the text field
                chipNumberField.setText(code);
            });
        });
    }

    /**
     * Handles the update action for editing an animal.
     * Validates inputs, updates the Animal object, and attempts to save changes using the AnimalService.
     * Shows success or error alerts based on the result.
     *
     * @throws Exception if an error occurs during update.
     */
    @FXML
    public void handleUpdate() throws Exception {
        if (!validateInputs()) return;

        // Set basic animal properties
        currentAnimal.setName(nameField.getText().trim());
        currentAnimal.setSpecies(speciesComboBox.getValue());
        currentAnimal.setSex(sexComboBox.getValue());
        currentAnimal.setApproximateAge(ageSpinner.getValue());
        currentAnimal.setCollectedBy(collectedByField.getText().trim());
        currentAnimal.setAdmissionDate(DateUtils.localDateToUtcString(admissionDatePicker.getValue()));

        if (adoptedCheckBox.isSelected()) {
            currentAnimal.setAdopted(true);
            currentAnimal.setActive(false);
        } else {
            currentAnimal.setAdopted(false);
            currentAnimal.setActive(true);
        }

        if (neuteringDatePicker.getValue() != null) {
            currentAnimal.setNeuteringDate(DateUtils.localDateToUtcString(neuteringDatePicker.getValue()));
        } else {
            currentAnimal.setNeuteringDate(null);
        }

        // Handle barcode and chip number logic
        String currentFieldValue = chipNumberField.getText().trim();

        if (scannedChipNumber != null && !scannedChipNumber.isBlank()) {
            // A new code was scanned during this edit session
            currentAnimal.setBarcode(scannedChipNumber.trim());
            currentAnimal.setChipNumber(scannedChipNumber.trim());
        } else {
            // Field was edited manually or not changed
            currentAnimal.setChipNumber(currentFieldValue.isEmpty() ? null : currentFieldValue);

            // Only clear barcode if the value was manually changed from the original
            String originalValue = getOriginalChipValue(currentAnimal);
            if (!currentFieldValue.equals(originalValue)) {
                currentAnimal.setBarcode(null); // Manual edit, clear barcode
            }
            // If value unchanged, keep existing barcode
        }

        currentAnimal.setReasonForRescue(getTextAreaValue(rescueReasonArea));
        currentAnimal.setAilments(getTextAreaValue(ailmentsArea));

        Place selectedPlace = getSelectedPlace();
        if (selectedPlace != null) {
            currentAnimal.setPlaceId(selectedPlace.getId());
        }

        currentAnimal.setSynced(false);

        boolean updated = ServiceFactory.getAnimalService().updateAnimal(currentAnimal, true);
        if (updated) {
            NavigationHelper.showSuccessAlert("Exito", "Animal actualizado exitosamente.");
            NavigationHelper.goToAnimalModule(portalController);
        } else {
            NavigationHelper.showErrorAlert("Error", null, "Ocurrió un error al actualizar el animal.");
        }
    }

    /**
     * Retrieves the selected Place object based on the current value of the place ComboBox.
     *
     * @return The selected Place, or null if none is selected.
     */
    private Place getSelectedPlace() {
        String selectedName = placeComboBox.getValue();
        return allPlaces.stream()
                .filter(p -> p.getName().equals(selectedName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Validates all required input fields in the animal editing form.
     * Shows error alerts for any invalid or missing data.
     *
     * @return true if all inputs are valid, false otherwise.
     */
    private boolean validateInputs() {
        String name = safeTrim(nameField.getText().trim());
        String collectedBy = collectedByField.getText().trim();

        if(!name.isEmpty()){
            Pattern noSpecialChars = Pattern.compile("^[a-zA-Z0-9\\s]+$");
            if (!noSpecialChars.matcher(name).matches()) {
                NavigationHelper.showErrorAlert("Error", null, "El nombre no debe contener caracteres especiales.");
                return false;
            }
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

    /**
     * Returns the trimmed value of a TextArea, or null if empty.
     *
     * @param textArea The TextArea to extract text from.
     * @return The trimmed text, or null if empty.
     */
    private String getTextAreaValue(TextArea textArea) {
        String text = textArea.getText();
        return (text == null || text.trim().isEmpty()) ? null : text.trim();
    }

    /**
     * Returns a trimmed string value, or an empty string if the value is null.
     *
     * @param value The string to trim.
     * @return The trimmed string, or empty string if null.
     */
    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Gets the original chip value (chip number or barcode) that was displayed when the form was loaded.
     *
     * @param animal The Animal object being edited.
     * @return The original chip or barcode value, or empty string if none.
     */
    private String getOriginalChipValue(Animal animal) {
        if (animal.getChipNumber() != null && !animal.getChipNumber().isBlank()) {
            return animal.getChipNumber();
        } else if (animal.getBarcode() != null && !animal.getBarcode().isBlank()) {
            return animal.getBarcode();
        }
        return "";
    }

    @Override public void setPortalController(PortalController controller) {
        this.portalController = controller;
    }
    @FXML public void goToAnimalModule() {
        NavigationHelper.goToAnimalModule(portalController);
    }
}
