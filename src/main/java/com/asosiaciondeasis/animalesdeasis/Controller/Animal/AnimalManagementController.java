package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Util.BarcodeScannerUtil;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import com.asosiaciondeasis.animalesdeasis.Util.ScreenTasks;
import com.asosiaciondeasis.animalesdeasis.Util.SyncEventManager;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class AnimalManagementController implements IPortalAwareController {
    private static final Logger log = LoggerFactory.getLogger(AnimalManagementController.class);

    private static final int ROWS_PER_PAGE = 20;
    private static final String ALL_SPECIES = "Todas";

    @FXML private TableView<Animal> animalTable;
    @FXML private TableColumn<Animal, String> idAdmissionDate;
    @FXML private TableColumn<Animal, String> nameColumn;
    @FXML private TableColumn<Animal, String> speciesColumn;
    @FXML private TableColumn<Animal, String> sexColumn;
    @FXML private TableColumn<Animal, String> adoptedColumn;
    @FXML private TableColumn<Animal, Void> actionsColumn;
    @FXML private Pagination pagination;

    @FXML private Button toggleFiltersBtn;
    @FXML private HBox filtersBox;
    @FXML private ComboBox<String> speciesFilter;
    @FXML private DatePicker startDateFilter;
    @FXML private DatePicker endDateFilter;
    @FXML private CheckBox inactiveFilter;
    @FXML private Label resultsCountLabel;
    @FXML private TextField chipNumberFilter;

    private final BarcodeScannerUtil scannerUtil = new BarcodeScannerUtil();
    private String scannedChipNumber;
    private PortalController portalController;
    private List<Animal> filteredAnimals = Collections.emptyList();
    private boolean filtersVisible;
    private Runnable syncListener;
    private final ScreenTasks tasks = new ScreenTasks("animal-list");

    @Override
    public void setPortalController(PortalController portalController) {
        this.portalController = portalController;
    }

    @FXML
    public void initialize() {
        // Nothing here touches the database. This method runs on the JavaFX
        // application thread, and it used to open with getActiveAnimals(), so the
        // window froze until the query returned. The table is built empty and
        // filled from a background task instead.
        speciesFilter.getItems().setAll(ALL_SPECIES, "Perro", "Gato");
        speciesFilter.setValue(ALL_SPECIES);
        setUpTables();
        addActionButtons();
        animalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        animalTable.setPlaceholder(new Label("Cargando animales…"));
        setUpPagination();
        updateResultsCount();

        // SyncEventManager notifies from the synchronisation thread, and the
        // refresh touches the table, the pagination and the labels, so it has to
        // hop to the interface thread.
        syncListener = () -> Platform.runLater(this::refreshAnimalList);
        SyncEventManager.addListener(syncListener);

        refreshAnimalList();
    }

    /**
     * Runs a query off the interface thread and applies its result back on it.
     *
     * <p>Every database read in this screen goes through here. Doing it in one
     * place is what stops the next one from quietly being written inline again,
     * and it is also where the empty-table message is kept honest: "loading",
     * then either rows or a reason there are none.</p>
     */
    private void loadInBackground(Callable<List<Animal>> query, Consumer<List<Animal>> onLoaded) {
        tasks.submit(query, onLoaded, cause -> {
            log.error("Could not load animals", cause);
            animalTable.setPlaceholder(new Label("No se pudieron cargar los animales"));
            NavigationHelper.showErrorAlert("Error", "No se pudieron cargar los animales",
                    cause == null ? "Error desconocido" : cause.getMessage());
        });
    }

    /** Shows {@code animals} from the first page. Interface thread only. */
    private void showAnimals(List<Animal> animals) {
        filteredAnimals = animals;
        animalTable.setPlaceholder(new Label("No hay animales que coincidan con los filtros"));
        setUpPagination();
        updateResultsCount();
        animalTable.refresh();
    }

    private void setUpTables() {
        nameColumn.setCellValueFactory(cellData -> {
            String name = cellData.getValue().getName();
            return new ReadOnlyStringWrapper((name == null || name.isBlank()) ? "N/A" : name);
        });
        speciesColumn.setCellValueFactory(new PropertyValueFactory<>("species"));
        idAdmissionDate.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                DateUtils.formatUtcForDisplay(cellData.getValue().getAdmissionDate())));
        sexColumn.setCellValueFactory(new PropertyValueFactory<>("sex"));
        adoptedColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                cellData.getValue().isAdopted() ? "✅ Adoptado" : "❌ No"));
    }

    private void loadAnimals(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, filteredAnimals.size());

        if (fromIndex < filteredAnimals.size()) {
            animalTable.getItems().setAll(filteredAnimals.subList(fromIndex, toIndex));
        } else {
            animalTable.getItems().clear();
        }
    }

    private void setUpPagination() {
        int totalPages = (int) Math.ceil((double) filteredAnimals.size() / ROWS_PER_PAGE);
        pagination.setPageCount(Math.max(totalPages, 1));
        pagination.setCurrentPageIndex(0);

        // The table sits outside the Pagination, which is used only for its page
        // controls: the factory fills the table and hands back an empty node.
        // It runs on every page change, so no index listener is needed - adding
        // one here stacked another on each reload.
        pagination.setPageFactory(pageIndex -> {
            loadAnimals(pageIndex);
            return new Label();
        });
        loadAnimals(0);
    }

    private void updateResultsCount() {
        resultsCountLabel.setText("Total: " + filteredAnimals.size() + " animales");
    }

    @FXML
    public void handleScanChip() {
        // The scanner invokes this on the JavaFX application thread.
        scannerUtil.startScanning(code -> {
            scannedChipNumber = code;
            chipNumberFilter.setText(code);
        });
    }

    /** Shows or hides the filter row with a fade. */
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

    /** Applies the selected filters and reports how many animals matched. */
    @FXML
    public void handleApplyFilters() {
        LocalDate startDate = startDateFilter.getValue();
        LocalDate endDate = endDateFilter.getValue();
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            NavigationHelper.showErrorAlert("Error de fechas",
                    "La fecha de inicio no puede ser posterior a la fecha de fin",
                    "Por favor, selecciona un rango de fechas válido.");
            return;
        }

        animalTable.setPlaceholder(new Label("Buscando…"));
        loadInBackground(filteredQuery(), animals -> {
            showAnimals(animals);
            NavigationHelper.showSuccessAlert("Filtros aplicados", animals.isEmpty()
                    ? "No se encontraron animales con los filtros seleccionados."
                    : "Se encontraron " + animals.size() + " animales.");
        });
    }

    /**
     * The query the filter fields describe, with their values read now so the
     * background task touches no controls.
     */
    private Callable<List<Animal>> filteredQuery() {
        String species = getFilterValue(speciesFilter.getValue());
        String chipNumber = (scannedChipNumber != null && !scannedChipNumber.isBlank())
                ? scannedChipNumber.trim()
                : getFilterValue(chipNumberFilter.getText());
        LocalDate startDate = startDateFilter.getValue();
        LocalDate endDate = endDateFilter.getValue();
        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;
        boolean showInactive = inactiveFilter.isSelected();

        return () -> ServiceFactory.getAnimalService()
                .findByFilters(species, start, end, chipNumber, showInactive);
    }

    /** Clears every filter and shows all active animals again. */
    @FXML
    public void handleClearFilters() {
        speciesFilter.setValue(ALL_SPECIES);
        startDateFilter.setValue(null);
        endDateFilter.setValue(null);
        inactiveFilter.setSelected(false);
        chipNumberFilter.clear();
        scannedChipNumber = null;

        refreshAnimalList();

        NavigationHelper.showInfoAlert("Filtros limpiados", "Se han eliminado todos los filtros. Mostrando todos los animales.");
    }

    @FXML
    public void handleCreateAnimal() {
        if (portalController != null) {
            portalController.loadContent("/fxml/Animal/CreateAnimal.fxml");
        }
    }

    /** Adds the per-row buttons: detail, edit and delete, or detail and reactivate for inactive animals. */
    private void addActionButtons() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = createButton("Editar", "edit-btn", "Editar");
            private final Button deleteBtn = createButton("Eliminar", "delete-btn", "Desactivar");
            private final Button detailBtn = createButton("Detalles", "detail-btn", "Detalles");
            private final Button reactivateBtn = createButton("Activar", "reactivate-btn", "Reactivar animal");

            {
                editBtn.setOnAction(event -> openEdit(rowAnimal()));
                deleteBtn.setOnAction(event -> deleteAnimal(rowAnimal()));
                detailBtn.setOnAction(event -> openDetail(rowAnimal()));
                reactivateBtn.setOnAction(event -> reactivateAnimal(rowAnimal()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                HBox buttonBox = inactiveFilter.isSelected()
                        ? new HBox(5, detailBtn, reactivateBtn)
                        : new HBox(5, detailBtn, editBtn, deleteBtn);
                buttonBox.setAlignment(Pos.CENTER);
                setGraphic(buttonBox);
            }

            private Animal rowAnimal() {
                return getTableView().getItems().get(getIndex());
            }

            private Button createButton(String text, String styleClass, String tooltipText) {
                Button button = new Button(text);
                button.setTooltip(new Tooltip(tooltipText));
                button.getStyleClass().add(styleClass);
                return button;
            }
        });
    }

    private void openEdit(Animal animal) {
        if (portalController != null) {
            portalController.<EditAnimalController>openScreen("/fxml/Animal/EditAnimal.fxml",
                    edit -> edit.setAnimalData(animal));
        }
    }

    private void openDetail(Animal animal) {
        if (portalController != null) {
            portalController.<DetailAnimalController>openScreen("/fxml/Animal/DetailAnimal.fxml",
                    detail -> detail.setAnimalDetails(animal));
        }
    }

    private void deleteAnimal(Animal animal) {
        boolean confirmed = NavigationHelper.showConfirmationAlert("Confirmar eliminación",
                "¿Estás seguro de que deseas eliminar este animal?",
                "Animal: " + animal.getName() + " - " + animal.getSpecies());
        if (!confirmed) {
            return;
        }
        tasks.submit(() -> {
                    ServiceFactory.getAnimalService().deleteAnimal(animal.getRecordNumber());
                    return null;
                },
                done -> {
                    refreshAnimalList();
                    NavigationHelper.showSuccessAlert("Éxito", "Animal eliminado correctamente.");
                },
                e -> {
                    log.error("Could not delete animal {}", animal.getRecordNumber(), e);
                    NavigationHelper.showErrorAlert("Error", "No se pudo eliminar el animal", e.getMessage());
                });
    }

    private void reactivateAnimal(Animal animal) {
        boolean confirmed = NavigationHelper.showConfirmationAlert("Confirmar reactivación",
                "¿Estás seguro de que deseas reactivar este animal?",
                "Animal: " + animal.getName() + " - " + animal.getSpecies());
        if (!confirmed) {
            return;
        }
        tasks.submit(() -> {
                    ServiceFactory.getAnimalService().reactivateAnimal(animal.getRecordNumber());
                    return null;
                },
                done -> {
                    refreshAnimalList();
                    NavigationHelper.showSuccessAlert("Éxito", "Animal reactivado correctamente.");
                },
                e -> {
                    log.error("Could not reactivate animal {}", animal.getRecordNumber(), e);
                    NavigationHelper.showErrorAlert("Error", "No se pudo reactivar el animal", e.getMessage());
                });
    }

    /** @return the value, or {@code null} when it is blank or the "all" option */
    private String getFilterValue(String value) {
        return (value != null && !value.isBlank() && !ALL_SPECIES.equals(value)) ? value : null;
    }

    private boolean hasActiveFilters() {
        return getFilterValue(speciesFilter.getValue()) != null
                || startDateFilter.getValue() != null
                || endDateFilter.getValue() != null
                || getFilterValue(chipNumberFilter.getText()) != null
                || (scannedChipNumber != null && !scannedChipNumber.isBlank())
                || inactiveFilter.isSelected();
    }

    /** Reloads the table with whatever the filter fields currently describe. */
    private void refreshAnimalList() {
        loadInBackground(hasActiveFilters()
                        ? filteredQuery()
                        : () -> ServiceFactory.getAnimalService().getActiveAnimals(),
                this::showAnimals);
    }

    @Override
    public void cleanup() {
        if (syncListener != null) {
            SyncEventManager.removeListener(syncListener);
            syncListener = null;
        }
        // Stops an in-flight query from completing into a table that is no
        // longer on screen.
        tasks.close();
        scannerUtil.stopScanning();
    }
}
