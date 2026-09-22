package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.Animal.DetailAnimalController;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import com.asosiaciondeasis.animalesdeasis.Util.ScreenTasks;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class VaccineManagementController implements IPortalAwareController {
    private static final Logger log = LoggerFactory.getLogger(VaccineManagementController.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @FXML private Label animalInfoLabel;
    @FXML private TableView<Vaccine> vaccineTable;
    @FXML private TableColumn<Vaccine, String> vaccineNameColumn;
    @FXML private TableColumn<Vaccine, String> vaccinationDateColumn;
    @FXML private TableColumn<Vaccine, Void> actionsColumn;
    @FXML private Label totalVaccinesLabel;
    @FXML private Label lastVaccineLabel;

    private final ScreenTasks tasks = new ScreenTasks("vaccines");
    private PortalController portalController;
    private Animal currentAnimal;

    @FXML
    public void initialize() {
        vaccineNameColumn.setCellValueFactory(new PropertyValueFactory<>("vaccineName"));
        vaccinationDateColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatDate(cellData.getValue().getVaccinationDate())));

        vaccineNameColumn.setResizable(false);
        vaccinationDateColumn.setResizable(false);
        actionsColumn.setResizable(false);
        addActionsButtons();
    }

    public void setCurrentAnimal(Animal animal) throws Exception {
        this.currentAnimal = animal;
        animalInfoLabel.setText("Animal: " + animal.getName());
        loadVaccinesForAnimal();
    }

    private void loadVaccinesForAnimal() throws Exception {
        List<Vaccine> vaccines = ServiceFactory.getVaccineService()
                .getVaccinesByAnimal(currentAnimal.getRecordNumber());

        vaccineTable.setItems(FXCollections.observableArrayList(vaccines));
        totalVaccinesLabel.setText(String.valueOf(vaccines.size()));
        lastVaccineLabel.setText(vaccines.stream()
                .map(vaccine -> DateUtils.utcStringToLocalDate(vaccine.getVaccinationDate()))
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .map(date -> date.format(DATE_FORMAT))
                .orElse("N/A"));
    }

    private static String formatDate(String stored) {
        LocalDate date = DateUtils.utcStringToLocalDate(stored);
        return date != null ? date.format(DATE_FORMAT) : "N/A";
    }

    private void addActionsButtons() {
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final HBox buttonsContainer = new HBox(10);
            private final Button editButton = new Button("Editar");
            private final Button deleteButton = new Button("Eliminar");

            {
                editButton.getStyleClass().addAll("action-btn", "edit-btn");
                deleteButton.getStyleClass().addAll("action-btn", "delete-btn");

                editButton.setPrefSize(65, 28);
                editButton.setMinSize(65, 28);
                editButton.setMaxSize(65, 28);

                editButton.setOnAction(event -> onEditVaccine(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(event -> {
                    Vaccine vaccine = getTableView().getItems().get(getIndex());
                    boolean confirmed = NavigationHelper.showConfirmationAlert("Confirmar eliminación",
                            "¿Estás seguro de que deseas eliminar esta vacuna?",
                            "Vacuna: " + vaccine.getVaccineName() + " - " + formatDate(vaccine.getVaccinationDate()));
                    if (confirmed) {
                        onDeleteVaccine(vaccine);
                    }
                });

                buttonsContainer.getChildren().addAll(editButton, deleteButton);
                buttonsContainer.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttonsContainer);
            }
        });
    }

    @FXML
    public void onCreateNewVaccine() {
        openModal("/fxml/Vaccine/CreateVaccine.fxml", "Nueva Vacuna", (CreateVaccineController controller) -> {
            controller.setAnimalInfo(currentAnimal.getName(), currentAnimal.getRecordNumber());
            controller.setOnVaccineCreated(vaccine -> saveAndReload(
                    () -> ServiceFactory.getVaccineService().registerVaccine(vaccine),
                    "Vacuna registrada correctamente", "No se pudo registrar la vacuna"));
        });
    }

    private void onEditVaccine(Vaccine vaccine) {
        openModal("/fxml/Vaccine/EditVaccine.fxml", "Editar Vacuna", (EditVaccineController controller) -> {
            controller.setAnimalInfo(currentAnimal.getName());
            controller.setVaccineData(vaccine);
            controller.setOnVaccineUpdated(updated -> saveAndReload(
                    () -> ServiceFactory.getVaccineService().updateVaccine(updated),
                    "Vacuna actualizada correctamente", "No se pudo actualizar la vacuna"));
        });
    }

    /**
     * Opens a small modal form over the main window, which keeps the task
     * focused and blocks the table until it is done.
     */
    private <C> void openModal(String fxmlPath, String title, Consumer<C> setup) {
        try {
            FXMLLoader loader = new FXMLLoader(VaccineManagementController.class.getResource(fxmlPath));
            Parent root = loader.load();
            setup.accept(loader.getController());

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(vaccineTable.getScene().getWindow());
            stage.setResizable(false);
            stage.setScene(new Scene(root));
            stage.getIcons().add(new Image(Objects.requireNonNull(
                    VaccineManagementController.class.getResourceAsStream("/images/AdeAsisLogo.png"))));
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Could not open {}", fxmlPath, e);
            NavigationHelper.showErrorAlert("Error", "No se pudo abrir el formulario de vacuna", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Save {
        void run() throws Exception;
    }

    private void saveAndReload(Save save, String successMessage, String failureHeader) {
        try {
            save.run();
            loadVaccinesForAnimal();
            NavigationHelper.showSuccessAlert("Éxito", successMessage);
        } catch (Exception e) {
            log.error(failureHeader, e);
            NavigationHelper.showErrorAlert("Error", failureHeader, e.getMessage());
        }
    }

    /**
     * Deletes off the interface thread: the remote half waits on Firestore, and
     * awaiting it here froze the window for as long as the network took.
     */
    private void onDeleteVaccine(Vaccine vaccine) {
        vaccineTable.setDisable(true);
        tasks.submit(() -> {
                    ServiceFactory.getSyncService().deleteVaccineAndSync(vaccine);
                    return null;
                },
                done -> {
                    vaccineTable.setDisable(false);
                    saveAndReload(() -> { }, "Vacuna eliminada correctamente.", "No se pudo recargar las vacunas");
                },
                cause -> {
                    vaccineTable.setDisable(false);
                    log.error("Could not delete vaccine {}", vaccine.getId(), cause);
                    NavigationHelper.showErrorAlert("Error", "No se pudo eliminar la vacuna", cause.getMessage());
                });
    }

    @FXML
    private void goBackDetail() {
        if (currentAnimal != null && portalController != null) {
            portalController.<DetailAnimalController>openScreen("/fxml/Animal/DetailAnimal.fxml",
                    detail -> detail.setAnimalDetails(currentAnimal));
        }
    }

    @Override
    public void setPortalController(PortalController controller) {
        this.portalController = controller;
    }

    /**
     * Keeps a pending deletion from reloading this table and raising its dialog
     * over the screen that replaced it. The deletion itself is already committed
     * locally by then; only the confirmation is dropped.
     */
    @Override
    public void cleanup() {
        tasks.close();
    }
}
