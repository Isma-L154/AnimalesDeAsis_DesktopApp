package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.DuplicateChipException;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Util.BarcodeScannerUtil;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.DatePickers;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.FieldValidation;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * What the create and edit animal forms share: the fields, their limits, their
 * validation, and how they map onto an {@link Animal}.
 *
 * <p>The two screens used to carry identical copies of all of this, so a rule
 * fixed in one stayed broken in the other.</p>
 */
public abstract class AnimalFormController implements IPortalAwareController {
    private static final Logger log = LoggerFactory.getLogger(AnimalFormController.class);

    /** Letters in any alphabet, so "Toño" and "Muñeca" are accepted, plus digits and spaces. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N}\\s]+$");
    private static final int MAX_AGE = 50;
    private static final int MAX_RESCUE_REASON = 300;
    private static final int MAX_AILMENTS = 500;

    @FXML protected TextField nameField;
    @FXML protected ComboBox<String> speciesComboBox;
    @FXML protected ComboBox<String> sexComboBox;
    @FXML protected Spinner<Integer> ageSpinner;
    @FXML protected ComboBox<Place> placeComboBox;
    @FXML protected TextField collectedByField;
    @FXML protected DatePicker admissionDatePicker;
    @FXML protected DatePicker neuteringDatePicker;
    @FXML protected TextField chipNumberField;
    @FXML protected TextArea rescueReasonArea;
    @FXML protected TextArea ailmentsArea;

    protected final FieldValidation validation = new FieldValidation();
    private final BarcodeScannerUtil scannerUtil = new BarcodeScannerUtil();

    /** The code read by the scanner in this session, which is saved as the barcode too. */
    protected String scannedChipNumber;
    protected PortalController portalController;

    @FXML
    public void initialize() {
        speciesComboBox.setItems(FXCollections.observableArrayList("Perro", "Gato"));
        sexComboBox.setItems(FXCollections.observableArrayList("Macho", "Hembra"));
        ageSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, MAX_AGE, 0));
        limitLength(rescueReasonArea, MAX_RESCUE_REASON);
        limitLength(ailmentsArea, MAX_AILMENTS);

        DatePickers.useDisplayFormat(admissionDatePicker);
        DatePickers.useDisplayFormat(neuteringDatePicker);
        DatePickers.disableFutureDates(admissionDatePicker);

        placeComboBox.setEditable(false);
        try {
            placeComboBox.setItems(FXCollections.observableArrayList(
                    ServiceFactory.getPlaceService().getAllPlaces()));
        } catch (Exception e) {
            log.error("Could not load places", e);
            NavigationHelper.showErrorAlert("Error", "No se pudieron cargar los lugares", e.getMessage());
        }
    }

    private static void limitLength(TextArea area, int max) {
        area.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() > max ? null : change));
    }

    static boolean isValidName(String name) {
        return name.isEmpty() || NAME.matcher(name).matches();
    }

    @FXML
    public void handleScanBarcode() {
        // The scanner invokes this on the JavaFX application thread.
        scannerUtil.startScanning(code -> {
            scannedChipNumber = code;
            chipNumberField.setText(code);
        });
    }

    /** Marks every invalid field in place and focuses the first one. */
    protected boolean validateInputs() {
        validation.clear();
        boolean valid = validation.require(nameField, isValidName(text(nameField)),
                "El nombre solo puede tener letras, números y espacios");
        valid &= validation.require(speciesComboBox, speciesComboBox.getValue() != null,
                "Seleccione una especie");
        valid &= validation.require(sexComboBox, sexComboBox.getValue() != null,
                "Seleccione el sexo");
        valid &= validation.require(ageSpinner, ageSpinner.getValue() != null && ageSpinner.getValue() > 0,
                "Ingrese una edad válida");
        valid &= validation.require(admissionDatePicker, admissionDatePicker.getValue() != null,
                "Seleccione la fecha de ingreso");
        valid &= validation.require(placeComboBox, placeComboBox.getValue() != null,
                "Seleccione un lugar");
        valid &= validation.requireText(collectedByField, text(collectedByField),
                "Indique quién recogió al animal");
        if (!valid) {
            validation.focusFirstError();
        }
        return valid;
    }

    /** Copies every field the two forms share onto {@code animal}. */
    protected void writeCommonFields(Animal animal) {
        animal.setName(text(nameField));
        animal.setSpecies(speciesComboBox.getValue());
        animal.setSex(sexComboBox.getValue());
        animal.setApproximateAge(ageSpinner.getValue());
        animal.setPlaceId(placeComboBox.getValue().id());
        animal.setCollectedBy(text(collectedByField));
        animal.setAdmissionDate(DateUtils.localDateToUtcString(admissionDatePicker.getValue()));
        animal.setNeuteringDate(DateUtils.localDateToUtcString(neuteringDatePicker.getValue()));
        animal.setReasonForRescue(textOrNull(rescueReasonArea));
        animal.setAilments(textOrNull(ailmentsArea));
    }

    /** Fills the shared fields from an existing record. */
    protected void readCommonFields(Animal animal) {
        nameField.setText(animal.getName());
        speciesComboBox.setValue(animal.getSpecies());
        sexComboBox.setValue(animal.getSex());
        ageSpinner.getValueFactory().setValue(animal.getApproximateAge());
        collectedByField.setText(animal.getCollectedBy());
        admissionDatePicker.setValue(DateUtils.utcStringToLocalDate(animal.getAdmissionDate()));
        neuteringDatePicker.setValue(DateUtils.utcStringToLocalDate(animal.getNeuteringDate()));
        rescueReasonArea.setText(animal.getReasonForRescue());
        ailmentsArea.setText(animal.getAilments());
        placeComboBox.getItems().stream()
                .filter(place -> place.id() == animal.getPlaceId())
                .findFirst()
                .ifPresent(placeComboBox::setValue);
    }

    @FunctionalInterface
    protected interface Save {
        void run() throws Exception;
    }

    /**
     * Runs the save and reports the outcome: a duplicate chip is marked on the
     * chip field, any other failure is an error dialog, and success returns to
     * the animal list.
     */
    protected void persist(Save save, String successMessage) {
        try {
            save.run();
        } catch (DuplicateChipException e) {
            validation.reject(chipNumberField, "Ya existe un animal con este número de chip");
            validation.focusFirstError();
            return;
        } catch (Exception e) {
            log.error("Could not save animal", e);
            NavigationHelper.showErrorAlert("Error", "No se pudo guardar el animal", e.getMessage());
            return;
        }
        NavigationHelper.showSuccessAlert("Éxito", successMessage);
        goToAnimalModule();
    }

    protected static String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private static String textOrNull(TextArea area) {
        String text = area.getText();
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    @Override
    public void setPortalController(PortalController controller) {
        this.portalController = controller;
    }

    @FXML
    public void goToAnimalModule() {
        NavigationHelper.goToAnimalModule(portalController);
    }

    /** Releases the camera if the user leaves mid-scan. */
    @Override
    public void cleanup() {
        scannerUtil.stopScanning();
    }
}
