package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.animation.FadeTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AnimalManagementController implements IPortalAwareController {

    private final int ROWS_PER_PAGE = 20;
    // Table components
    @FXML private TableView<Animal> animalTable;
    @FXML private TableColumn<Animal, String> idAdmissionDate;
    @FXML private TableColumn<Animal, String> nameColumn;
    @FXML private TableColumn<Animal, String> speciesColumn;
    @FXML private TableColumn<Animal, String> sexColumn;
    @FXML private TableColumn<Animal, String> adoptedColumn;
    @FXML private TableColumn<Animal, Void> actionsColumn;
    @FXML private Pagination pagination;
    @FXML private Button CreateAnimal;

    // Filter components
    @FXML private Button toggleFiltersBtn;
    @FXML private HBox filtersBox;
    @FXML private ComboBox<String> speciesFilter;
    @FXML private DatePicker startDateFilter;
    @FXML private DatePicker endDateFilter;
    @FXML private ComboBox<String> adoptionFilter;
    @FXML private Button applyFiltersBtn;
    @FXML private Button clearFiltersBtn;
    @FXML private Label resultsCountLabel;

    private PortalController portalController;
    private List<Animal> allAnimals;
    private List<Animal> filteredAnimals;
    private boolean filtersVisible = false;

    @Override
    public void setPortalController(PortalController portalController) {
        this.portalController = portalController;
    }

    @FXML
    public void initialize() {
        try {
            // Initialize the animal table and pagination
            allAnimals = ServiceFactory.getAnimalService().getActiveAnimals();
            filteredAnimals = allAnimals; // Initially, all animals are shown
            initializeComboBoxes();
            setUpTables(); //Set up the table columns and pagination
            addActionButtons();
            setUpPagination(); // Load the first page of animals
            updateResultsCount();
            animalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void setUpTables() {
        // Initialize the table columns and pagination

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        speciesColumn.setCellValueFactory(new PropertyValueFactory<>("species"));

        idAdmissionDate.setCellValueFactory(cellData -> {
            String isoDate = cellData.getValue().getAdmissionDate(); //admissionDate is in ISO format (yyyy-MM-dd)
            LocalDate parsedDate = LocalDate.parse(isoDate);
            String formattedDate = parsedDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            return new SimpleStringProperty(formattedDate);
        });

        sexColumn.setCellValueFactory(new PropertyValueFactory<>("sex"));

        adoptedColumn.setCellValueFactory(new PropertyValueFactory<>("adopted"));
        adoptedColumn.setCellValueFactory(cellData -> {
            boolean isAdopted = cellData.getValue().isAdopted();
            String status = isAdopted ? "✅ Adoptado" : "❌ No";
            return new ReadOnlyStringWrapper(status);
        });
    }

    private void loadAnimals(int pageIndex) {
        // Load animals for the specified page index
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, filteredAnimals.size());

        if (fromIndex < filteredAnimals.size()) {
            List<Animal> pageAnimals = filteredAnimals.subList(fromIndex, toIndex);
            animalTable.getItems().setAll(pageAnimals);
        } else {
            animalTable.getItems().clear();
        }
    }

    private void setUpPagination() {
        int totalAnimals = allAnimals.size();
        int totalPages = (int) Math.ceil((double) totalAnimals / ROWS_PER_PAGE);

        pagination.setPageCount(totalPages);
        pagination.setCurrentPageIndex(0);

        pagination.setPageFactory(pageIndex -> {
            loadAnimals(pageIndex);
            return animalTable;
        });

        loadAnimals(0); // Load the first page of animals
    }

    /**
     * Filter section
     */
    private void updateResultsCount() {
        resultsCountLabel.setText("Total: " + filteredAnimals.size() + " animales");
    }

    @FXML
    public void handleToggleFilters() {
        filtersVisible = !filtersVisible;

        FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), filtersBox);

        if (filtersVisible) {
            filtersBox.setVisible(true);
            filtersBox.setManaged(true);
            fadeTransition.setFromValue(0.0);
            fadeTransition.setToValue(1.0);
            toggleFiltersBtn.setText("Ocultar Filtros");
        } else {
            fadeTransition.setFromValue(1.0);
            fadeTransition.setToValue(0.0);
            fadeTransition.setOnFinished(e -> {
                filtersBox.setVisible(false);
                filtersBox.setManaged(false);
            });
            toggleFiltersBtn.setText("Mostrar Filtros");
        }

        fadeTransition.play();
    }

    @FXML
    public void handleApplyFilters() {
        try {
            String species = getFilterValue(speciesFilter.getValue());
            LocalDate startDate = startDateFilter.getValue();
            LocalDate endDate = endDateFilter.getValue();

            String startDateStr = startDate != null ? startDate.toString() : null;
            String endDateStr = endDate != null ? endDate.toString() : null;

            Boolean adopted = getAdoptionStatus(adoptionFilter.getValue());

            // Validación de fechas
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                NavigationHelper.showErrorAlert("Error de fechas",
                        "La fecha de inicio no puede ser posterior a la fecha de fin",
                        "Por favor, selecciona un rango de fechas válido.");
                return;
            }

            filteredAnimals = ServiceFactory.getAnimalService().findByFilters(species, startDateStr, endDateStr, adopted);
            setUpPagination();
            updateResultsCount();

            String message = filteredAnimals.isEmpty() ?
                    "No se encontraron animales con los filtros seleccionados." :
                    "Se encontraron " + filteredAnimals.size() + " animales.";

            NavigationHelper.showSuccessAlert("Filtros aplicados", message);

        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error aplicando filtros",
                    "No se pudieron aplicar los filtros", e.getMessage());
        }
    }

    @FXML
    public void handleClearFilters() {

        speciesFilter.setValue("");
        startDateFilter.setValue(null);
        endDateFilter.setValue(null);
        adoptionFilter.setValue("");


        filteredAnimals = allAnimals;
        setUpPagination();
        updateResultsCount();

        NavigationHelper.showInfoAlert("Filtros limpiados", "Se han eliminado todos los filtros. Mostrando todos los animales.");
    }

    @FXML
    public void handleCreateAnimal() {
        if (portalController != null) {
            portalController.loadContent("/fxml/Animal/CreateAnimal.fxml");
        }
    }

    private void addActionButtons(){
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = createButton("Editar", "edit-btn", "Editar");
            private final Button deleteBtn = createButton("Eliminar", "delete-btn", "Desactivar");
            private final Button detailBtn = createButton("Detalles", "detail-btn", "Detalles");
            {
                editBtn.setOnAction(event -> handleEditAnimal());
                deleteBtn.setOnAction(event -> handleDeleteAnimal());
                detailBtn.setOnAction(event -> handleDetailAnimal());
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttonBox = new HBox(5, detailBtn, editBtn, deleteBtn);
                    buttonBox.setAlignment(Pos.CENTER);
                    setGraphic(buttonBox);
                }
            }

            private Button createButton(String text, String styleClass, String tooltipText) {
                Button button = new Button(text);
                button.setTooltip(new Tooltip(tooltipText));
                button.getStyleClass().add(styleClass);
                return button;
            }
            private void handleEditAnimal() {
                Animal animal = getTableView().getItems().get(getIndex());
                if (portalController != null) {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Animal/EditAnimal.fxml"));
                        Parent root = loader.load();
                        EditAnimalController editController = loader.getController();
                        editController.setPortalController(portalController);
                        editController.setAnimalData(animal);
                        portalController.setContent(root);
                    } catch (Exception e) {
                        NavigationHelper.showErrorAlert("Error", "No se pudo cargar el formulario de edición", e.getMessage());
                    }
                }
            }
            private void handleDeleteAnimal() {
                Animal animal = getTableView().getItems().get(getIndex());

                boolean confirmed = NavigationHelper.showConfirmationAlert("Confirmar eliminación",
                        "¿Estás seguro de que deseas eliminar este animal?",
                        "Animal: " + animal.getName() + " - " + animal.getSpecies());

                if (confirmed) {
                    try {
                        ServiceFactory.getAnimalService().deleteAnimal(animal.getRecordNumber());
                        refreshAnimalList();
                        NavigationHelper.showSuccessAlert("Éxito", "Animal eliminado correctamente.");
                    } catch (Exception e) {
                        NavigationHelper.showErrorAlert("Error", "No se pudo eliminar el animal", e.getMessage());
                    }
                }
            }
            private void handleDetailAnimal() {
                Animal animal = getTableView().getItems().get(getIndex());
                if (portalController != null) {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Animal/DetailAnimal.fxml"));
                        Parent root = loader.load();
                        DetailAnimalController detailController = loader.getController();
                        detailController.setPortalController(portalController);
                        detailController.setAnimalDetails(animal, ServiceFactory.getPlaceService().getAllPlaces());
                        portalController.setContent(root);
                    } catch (Exception e) {
                        NavigationHelper.showErrorAlert("Error", "No se pudo cargar los detalles del animal", e.getMessage());
                    }
                }
            }
        });
    }

    private String getFilterValue(String value) {
        return (value != null && !value.trim().isEmpty()) ? value : null;
    }

    private Boolean getAdoptionStatus(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return "Adoptado".equals(value);
    }

    private boolean hasActiveFilters() {
        return (speciesFilter.getValue() != null && !speciesFilter.getValue().isEmpty()) ||
                startDateFilter.getValue() != null ||
                endDateFilter.getValue() != null ||
                (adoptionFilter.getValue() != null && !adoptionFilter.getValue().isEmpty());
    }

    private void refreshAnimalList() throws Exception {
        allAnimals = ServiceFactory.getAnimalService().getActiveAnimals();

        if (hasActiveFilters()) {
            handleApplyFilters();
        } else {
            filteredAnimals = allAnimals;
            setUpPagination();
            updateResultsCount();
            animalTable.refresh();
        }
    }
    /**
     * We are doing this to initialize the combo boxes with default values, because in the FXML file
     * the system does not allow setting default values for ComboBoxes.
     */
    private void initializeComboBoxes() {

        speciesFilter.getItems().clear();
        speciesFilter.getItems().addAll("Todas", "Perro", "Gato");
        speciesFilter.setValue("Todas");


        adoptionFilter.getItems().clear();
        adoptionFilter.getItems().addAll("Todos", "Adoptado", "No Adoptado");
        adoptionFilter.setValue("Todos");
    }
}
