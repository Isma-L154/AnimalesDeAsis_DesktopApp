package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.Animal.DetailAnimalController;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Service.SyncService;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;

import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Parent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class VaccineManagementController implements IPortalAwareController {

    @FXML private Label animalInfoLabel;
    @FXML private TableView<Vaccine> vaccineTable;
    @FXML private TableColumn<Vaccine, String> vaccineNameColumn;
    @FXML private TableColumn<Vaccine, String> vaccinationDateColumn;
    @FXML private TableColumn<Vaccine, Void> actionsColumn;
    @FXML private Label totalVaccinesLabel;
    @FXML private Label lastVaccineLabel;

    private PortalController portalController;
    private Animal currentAnimal;

    /**
     * Initializes the controller and sets up the vaccine table columns.
     * Called automatically after the FXML fields are injected.
     *
     * @throws Exception if there is an error during initialization.
     */
    @FXML
    public void initialize() throws Exception {
        // Initialize the vaccine table columns
        setupTableColumns();

    }

    /**
     * Sets the current animal for which vaccines are managed.
     * Updates the animal info label, loads vaccines, and sets up action buttons.
     *
     * @param animal The Animal object to manage vaccines for.
     * @throws Exception if there is an error loading vaccines.
     */
    public void setCurrentAnimal(Animal animal) throws Exception {
        this.currentAnimal = animal;
        updateAnimalInfoLabel();
        addActionsButtons();
        loadVaccinesForAnimal();
    }

    /**
     * Configures the columns of the vaccine table, including formatting the date column.
     */
    private void setupTableColumns() {
        vaccineNameColumn.setCellValueFactory(new PropertyValueFactory<>("vaccineName"));

        vaccinationDateColumn.setCellValueFactory(cellData -> {
            LocalDate date = DateUtils.utcStringToLocalDate(cellData.getValue().getVaccinationDate());
            String formattedDate = (date != null) ? date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "N/A";
            return new SimpleStringProperty(formattedDate);
        });

        vaccineNameColumn.setResizable(false);
        vaccinationDateColumn.setResizable(false);
        actionsColumn.setResizable(false);
    }

    /**
     * Updates the label displaying the current animal's name.
     */
    private void updateAnimalInfoLabel() {
        if (animalInfoLabel != null && currentAnimal != null) {
            animalInfoLabel.setText("Animal: " + currentAnimal.getName());
        }
    }
    /**
     * Loads the list of vaccines for the current animal and updates the table and summary labels.
     *
     * @throws Exception if there is an error retrieving vaccines.
     */
    private void loadVaccinesForAnimal() throws Exception {
        if (currentAnimal == null) return;

        List<Vaccine> vaccines = ServiceFactory.getVaccineService()
                .getVaccinesByAnimal(currentAnimal.getRecordNumber());

        vaccineTable.setItems(FXCollections.observableArrayList(vaccines));
        updateSummary(vaccines);
    }

    /**
     * Updates the summary labels for total vaccines and the date of the last vaccine.
     *
     * @param vaccines The list of vaccines to summarize.
     */
    private void updateSummary(List<Vaccine> vaccines) {
        totalVaccinesLabel.setText(String.valueOf(vaccines.size()));

        if (!vaccines.isEmpty()) {
            LocalDate lastDate = vaccines.stream()
                    .map(vaccine -> DateUtils.utcStringToLocalDate(vaccine.getVaccinationDate()))
                    .filter(date -> date != null)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            String formattedLastDate = (lastDate != null)
                    ? lastDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    : "N/A";

            lastVaccineLabel.setText(formattedLastDate);
        } else {
            lastVaccineLabel.setText("N/A");
        }
    }

    /**
     * Adds edit and delete action buttons to each row in the vaccine table.
     * Configures the button actions for editing and deleting vaccines.
     */
    private void addActionsButtons() {
        actionsColumn.setCellFactory(column -> new TableCell<Vaccine, Void>() {
            private final HBox buttonsContainer = new HBox(10);
            private final Button editButton = new Button("Editar");
            private final Button deleteButton = new Button("Eliminar");

            {
                editButton.getStyleClass().addAll("action-btn", "edit-btn");
                deleteButton.getStyleClass().addAll("action-btn", "delete-btn");

                editButton.setPrefSize(65, 28);
                editButton.setMinSize(65, 28);
                editButton.setMaxSize(65, 28);

                editButton.setOnAction(event -> {
                    Vaccine vaccine = getTableView().getItems().get(getIndex());
                    onEditVaccine(vaccine);
                });

                deleteButton.setOnAction(event -> {
                    Vaccine vaccine = getTableView().getItems().get(getIndex());


                    boolean confirmed = NavigationHelper.showConfirmationAlert("Confirmar eliminación",
                            "¿Estás seguro de que deseas eliminar esta vacuna?",
                            "Vacuna: " + vaccine.getVaccineName() + " - " + vaccine.getVaccinationDate());

                    if(confirmed) {
                        onDeleteVaccine(vaccine);
                    }
                });

                buttonsContainer.getChildren().addAll(editButton, deleteButton);
                buttonsContainer.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(buttonsContainer);
                }
            }
        });
    }

    /**
     * Opens the window to create a new vaccine for the current animal.
     * Handles the creation callback and updates the vaccine list upon success.
     */
    @FXML
    public void onCreateNewVaccine() {
        try{
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Vaccine/CreateVaccine.fxml"));
            Parent root = loader.load();
            CreateVaccineController controller = loader.getController();
            controller.setAnimalInfo(currentAnimal.getName(), currentAnimal.getRecordNumber());

            controller.setOnVaccineCreated(vaccine -> {
                try {
                    ServiceFactory.getVaccineService().registerVaccine(vaccine);
                    loadVaccinesForAnimal();

                    NavigationHelper.showSuccessAlert("Éxito", "Vacuna registrada correctamente");
                } catch (Exception e) {
                    NavigationHelper.showErrorAlert("Error", "No se pudo registrar la vacuna: ", e.getMessage());
                }
            });

            /**
             * Create a new modal stage for the vaccine creation, because we want to make something smaller and focused
             * than the main window, so the user can focus on the task of creating a new vaccine
             * */

            Stage stage = new Stage();
            stage.setTitle("Nueva Vacuna");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(vaccineTable.getScene().getWindow());
            stage.setResizable(false);
            stage.setScene(new Scene(root));

            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/AdeAsisLogo.png"));
                stage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se pudo cargar el icono del modal: " + e.getMessage());
            }

            stage.showAndWait();
        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", "No se pudo abrir la ventana de creación de vacuna: ", e.getMessage());
        }
    }

    /**
     * Opens the window to edit the selected vaccine.
     * Handles the update callback and refreshes the vaccine list upon success.
     *
     * @param vaccine The Vaccine object to edit.
     */
    private void onEditVaccine(Vaccine vaccine) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Vaccine/EditVaccine.fxml"));
            Parent root = loader.load();
            EditVaccineController controller = loader.getController();
            controller.setAnimalInfo(currentAnimal.getName());
            controller.setVaccineData(vaccine);

            controller.setOnVaccineUpdated(updatedVaccine -> {
                try {
                    ServiceFactory.getVaccineService().updateVaccine(updatedVaccine, true);
                    loadVaccinesForAnimal();
                    NavigationHelper.showSuccessAlert("Éxito", "Vacuna actualizada correctamente");
                } catch (Exception e) {
                    NavigationHelper.showErrorAlert("Error", "No se pudo actualizar la vacuna: ", e.getMessage());
                }
            });

            /**
             * Create a new modal stage for the vaccine edition, because we want to make something smaller and focused
             * than the main window, so the user can focus on the task of editing a vaccine
             * */

            Stage stage = new Stage();
            stage.setTitle("Editar Vacuna");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(vaccineTable.getScene().getWindow());
            stage.setResizable(false);
            stage.setScene(new Scene(root));

            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/AdeAsisLogo.png"));
                stage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se pudo cargar el icono del modal: " + e.getMessage());
            }

            stage.showAndWait();
        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", "No se pudo abrir la ventana de edición de vacuna: ", e.getMessage());
        }
    }

    /**
     * Deletes the selected vaccine from both local storage and Firebase, then refreshes the vaccine list.
     *
     * @param vaccine The Vaccine object to delete.
     */
    private void onDeleteVaccine(Vaccine vaccine) {
        try {
            // Delete it in Firebase and Local
            ServiceFactory.getSyncService().deleteVaccineAndSync(vaccine);
            loadVaccinesForAnimal();
            NavigationHelper.showSuccessAlert(
                    "Éxito",
                    "Vacuna eliminada correctamente."
            );
        } catch (Exception e) {
            NavigationHelper.showErrorAlert(
                    "Error",
                    "No se pudo eliminar la vacuna",
                    e.getMessage()
            );
        }
    }

    /**
     * Navigates back to the animal detail view for the current animal.
     * Loads the detail view and sets the animal details.
     */
    @FXML
    private void goBackDetail() {
        if (currentAnimal != null && portalController != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Animal/DetailAnimal.fxml"));
                Parent root = loader.load();
                DetailAnimalController detailController = loader.getController();
                detailController.setPortalController(portalController);
                detailController.setAnimalDetails(currentAnimal, ServiceFactory.getPlaceService().getAllPlaces());
                portalController.setContent(root);

            } catch (Exception e) {
                NavigationHelper.showErrorAlert("Error", "No se pudo cargar los detalles del animal", e.getMessage());
            }
        } else {
            NavigationHelper.showErrorAlert("Error", "No se puede regresar", "Datos del animal no disponibles.");
        }
    }

    /**
     * Sets the portal controller reference for navigation purposes.
     *
     * @param controller The PortalController instance.
     */
    @Override
    public void setPortalController(PortalController controller) {this.portalController = controller;}

}
