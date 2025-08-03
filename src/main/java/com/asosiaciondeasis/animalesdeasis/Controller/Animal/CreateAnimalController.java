package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Service.Animal.AnimalService;
import com.asosiaciondeasis.animalesdeasis.Service.Place.PlaceService;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.BarcodeScannerUtil;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.time.LocalDate;
import java.util.List;

public class CreateAnimalController {

    @FXML private TextField nameField;
    @FXML private ComboBox<String> speciesComboBox;
    @FXML private ComboBox<String> sexComboBox;
    @FXML private TextField ageField;
    @FXML private ComboBox<Place> placeComboBox;
    @FXML private TextField collectedByField;
    @FXML private DatePicker admissionDatePicker;
    @FXML private DatePicker neuteringDatePicker;
    @FXML private TextField chipNumberField;
    @FXML private TextArea rescueReasonArea;
    @FXML private TextArea ailmentsArea;
    @FXML private Button scanBarcodeButton;

        public void initialize() {


        }


    public void handleScanBarcode() {
    }

    public void handleSave() {
    }
}
