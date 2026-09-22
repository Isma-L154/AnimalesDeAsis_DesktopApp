package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Controller.Vaccine.VaccineManagementController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Exporters.PDFAnimalExporter;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class DetailAnimalController implements IPortalAwareController {
    private static final Logger log = LoggerFactory.getLogger(DetailAnimalController.class);

    private static final String NO_INFO = "Sin información";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label nameLabel;
    @FXML private Label speciesLabel;
    @FXML private Label ageLabel;
    @FXML private Label sexLabel;
    @FXML private Label chipNumberLabel;
    @FXML private Label admissionDateLabel;
    @FXML private Label neuteringDateLabel;
    @FXML private Label collectedByLabel;
    @FXML private Label placeProvinceLabel;
    @FXML private Label rescueReasonLabel;
    @FXML private Label ailmentsLabel;
    @FXML private Button editButton;
    @FXML private Button downloadRecordBtn;
    @FXML private Button copyChipBtn;

    private Animal currentAnimal;
    private Place currentPlace;
    private PortalController portalController;

    /** Fills the view with {@code animal}, looking up the place it was rescued from. */
    public void setAnimalDetails(Animal animal) throws Exception {
        this.currentAnimal = animal;
        this.currentPlace = ServiceFactory.getPlaceService().getAllPlaces().stream()
                .filter(place -> place.id() == animal.getPlaceId())
                .findFirst()
                .orElse(null);

        nameLabel.setText(orNoInfo(animal.getName()));
        speciesLabel.setText(orNoInfo(animal.getSpecies()));
        int age = animal.getApproximateAge();
        ageLabel.setText(age + (age == 1 ? " año" : " años"));
        sexLabel.setText(orNoInfo(animal.getSex()));
        chipNumberLabel.setText(orNoInfo(animal.getChipNumber()));
        admissionDateLabel.setText(formatDate(animal.getAdmissionDate()));
        neuteringDateLabel.setText(formatDate(animal.getNeuteringDate()));
        collectedByLabel.setText(orNoInfo(animal.getCollectedBy()));
        rescueReasonLabel.setText(orNoInfo(animal.getReasonForRescue()));
        ailmentsLabel.setText(orNoInfo(animal.getAilments()));
        placeProvinceLabel.setText(currentPlace != null
                ? currentPlace.name() + ", " + currentPlace.provinceName()
                : NO_INFO);

        boolean hasChip = animal.getChipNumber() != null && !animal.getChipNumber().isBlank();
        copyChipBtn.setVisible(hasChip);
        copyChipBtn.setManaged(hasChip);

        // Inactive animals are read-only until reactivated from the list.
        editButton.setVisible(animal.isActive());
        editButton.setManaged(animal.isActive());
    }

    /**
     * Exports the record as a PDF. Vaccines are read off the interface thread;
     * the exporter asks for a destination and writes the file in the background.
     */
    @FXML
    private void downloadRecord() {
        if (currentAnimal == null) {
            NavigationHelper.showErrorAlert("Error", null, "No hay datos del animal para exportar.");
            return;
        }
        downloadRecordBtn.setDisable(true);
        downloadRecordBtn.setText("Generando...");

        Animal animal = currentAnimal;
        Place place = currentPlace;
        Stage owner = (Stage) downloadRecordBtn.getScene().getWindow();

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return ServiceFactory.getVaccineService().getVaccinesByAnimal(animal.getRecordNumber());
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                })
                .thenCompose(vaccines -> new PDFAnimalExporter()
                        .exportAnimalRecordWithDialog(animal, place, vaccines, owner))
                .whenComplete((filePath, throwable) -> Platform.runLater(() -> {
                    downloadRecordBtn.setDisable(false);
                    downloadRecordBtn.setText("Descargar PDF");

                    if (throwable != null) {
                        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                                ? throwable.getCause() : throwable;
                        log.error("Could not export animal record", cause);
                        NavigationHelper.showErrorAlert("Error", "Error al generar PDF",
                                cause.getMessage() != null ? cause.getMessage() : "Error desconocido");
                    } else if (filePath != null) {
                        NavigationHelper.showSuccessAlert("Éxito", "PDF generado correctamente en:\n" + filePath);
                    }
                }));
    }

    @FXML
    public void goToEditModule() {
        if (portalController != null && currentAnimal != null) {
            portalController.<EditAnimalController>openScreen("/fxml/Animal/EditAnimal.fxml",
                    edit -> edit.setAnimalData(currentAnimal));
        }
    }

    @FXML
    public void goToVaccineManagement() {
        if (portalController != null && currentAnimal != null) {
            portalController.<VaccineManagementController>openScreen("/fxml/Vaccine/VaccineManagement.fxml",
                    vaccines -> vaccines.setCurrentAnimal(currentAnimal));
        }
    }

    /** Copies the chip number to the clipboard and briefly confirms it on the button. */
    @FXML
    public void copyChipNumber() {
        String chipNumber = currentAnimal == null ? null : currentAnimal.getChipNumber();
        if (chipNumber == null || chipNumber.isBlank()) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(chipNumber);
        Clipboard.getSystemClipboard().setContent(content);

        String originalText = copyChipBtn.getText();
        copyChipBtn.setText("¡Copiado!");
        copyChipBtn.setDisable(true);

        PauseTransition pause = new PauseTransition(Duration.millis(1500));
        pause.setOnFinished(e -> {
            copyChipBtn.setText(originalText);
            copyChipBtn.setDisable(false);
        });
        pause.play();
    }

    private static String orNoInfo(String value) {
        return (value == null || value.isEmpty()) ? NO_INFO : value;
    }

    private static String formatDate(String stored) {
        if (stored == null || stored.isBlank()) {
            return NO_INFO;
        }
        try {
            LocalDate date = DateUtils.utcStringToLocalDate(stored);
            return date.format(DATE_FORMAT);
        } catch (RuntimeException e) {
            return "Fecha inválida";
        }
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
