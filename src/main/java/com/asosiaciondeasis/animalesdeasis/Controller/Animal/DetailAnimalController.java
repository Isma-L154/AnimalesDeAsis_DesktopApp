package com.asosiaciondeasis.animalesdeasis.Controller.Animal;


import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Controller.Vaccine.VaccineManagementController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Exporters.PDFAnimalExporter;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import javafx.util.Duration;

import javafx.scene.input.Clipboard;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DetailAnimalController implements IPortalAwareController {

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

    private List<Place> allPlaces;
    private Animal currentAnimal;
    private PortalController portalController;

    /**
     * Sets the details of the animal to be displayed in the view.
     * Populates all labels with the animal's information, including name, species, age, sex, chip number,
     * admission and neutering dates, collected by, rescue reason, ailments, and place.
     * Also updates the visibility of the copy chip button and edit button.
     *
     * @param animal     The Animal object whose details are to be shown.
     * @param allPlaces  The list of all available places for lookup.
     */
    public void setAnimalDetails(Animal animal, List<Place> allPlaces) {
        this.currentAnimal = animal;
        this.allPlaces = allPlaces;
        String admissionDateForm = DateUtils.utcStringToLocalDate(animal.getAdmissionDate()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String neuteringDateForm = "Sin información";
        if (animal.getNeuteringDate() != null) {
            try {
                neuteringDateForm = DateUtils.utcStringToLocalDate(animal.getNeuteringDate()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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
        updateCopyChipNumber();
        updateEditButtonVisibility();
    }

    /**
     * Handles the action to download the animal's record as a PDF file.
     * Fetches vaccines and place information asynchronously, generates the PDF, and provides user feedback.
     * Disables the download button during the process and restores it after completion.
     */
    @FXML
    private void downloadRecord() {
        if (currentAnimal == null) {
            NavigationHelper.showErrorAlert("Error", null, "No hay datos del animal para exportar.");
            return;
        }
        downloadRecordBtn.setDisable(true);
        downloadRecordBtn.setText("Generando...");

        CompletableFuture.supplyAsync(() -> {
                    try {
                        // Get vaccines for this animal
                        List<Vaccine> vaccines = ServiceFactory.getVaccineService()
                                .getVaccinesByAnimal(currentAnimal.getRecordNumber());

                        // Get place information
                        Place place = allPlaces.stream()
                                .filter(p -> p.getId() == currentAnimal.getPlaceId())
                                .findFirst()
                                .orElse(null);

                        return new Object[]{vaccines, place}; // Retornamos los datos preparados
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenCompose(data -> {
                    List<Vaccine> vaccines = (List<Vaccine>) data[0];
                    Place place = (Place) data[1];


                    PDFAnimalExporter exporter = new PDFAnimalExporter();
                    return exporter.exportAnimalRecordWithDialog(currentAnimal, place, vaccines,
                            (Stage) downloadRecordBtn.getScene().getWindow());
                })
                .whenComplete((filePath, throwable) -> {
                    Platform.runLater(() -> {
                        downloadRecordBtn.setDisable(false);
                        downloadRecordBtn.setText("Descargar PDF");

                        if (throwable != null) {
                            NavigationHelper.showErrorAlert("Error", "Error al generar PDF",
                                    throwable.getMessage() != null ? throwable.getMessage() : "Error desconocido");
                        } else if (filePath != null) {
                            NavigationHelper.showSuccessAlert("Éxito", "PDF generado correctamente en:\n" + filePath);
                        }
                    });
                });
    }

    /**
     * Navigates to the EditAnimal module, loading the edit form for the current animal.
     * Passes the current animal and portal controller to the edit controller.
     * Shows an error alert if the operation fails.
     */
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

    /**
     * Navigates to the VaccineManagement module for the current animal.
     * Loads the vaccine management view and passes the current animal and portal controller.
     * Shows an error alert if the operation fails.
     */
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

    /**
     * Updates the visibility of the edit button based on the current animal's active status.
     * The button is only visible and managed if the animal is active.
     */
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

    /**
     * Copies the chip number of the animal to the system clipboard.
     * Provides user feedback by temporarily changing the button text and disabling it.
     * Shows an error alert if the chip number is not available or the copy operation fails.
     */
    @FXML
    public void copyChipNumber() {
        String chipNumber = chipNumberLabel.getText();

        if (chipNumber == null || chipNumber.trim().isEmpty() || chipNumber.equals("Sin información")) {
            NavigationHelper.showErrorAlert("Info", "No hay número de chip para copiar",
                    "Este animal no tiene un número de chip registrado.");
            return;
        }

        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(chipNumber);
            clipboard.setContent(content);

            String originalText = copyChipBtn.getText();
            copyChipBtn.setText("¡Copiado!");
            copyChipBtn.setDisable(true);

            PauseTransition pause = new PauseTransition(Duration.millis(1500));
            pause.setOnFinished(e -> {
                copyChipBtn.setText(originalText);
                copyChipBtn.setDisable(false);
            });
            pause.play();

        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", "Error al copiar",
                    "No se pudo copiar el número de chip al portapapeles.");
        }
    }

    /**
     * Updates the visibility and management of the copy chip button based on the presence of a valid chip number.
     */
    private void updateCopyChipNumber() {
        if (copyChipBtn != null) {
            String chipNumber = chipNumberLabel.getText();
            boolean hasValidChip = chipNumber != null &&
                    !chipNumber.trim().isEmpty() &&
                    !chipNumber.equals("Sin información");

            copyChipBtn.setVisible(hasValidChip);
            copyChipBtn.setManaged(hasValidChip);
        }
    }

    /**
     * Validates a string value, returning "Sin información" if the value is null or empty.
     *
     * @param value The string to validate.
     * @return The original value if not null/empty, otherwise "Sin información".
     */
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
