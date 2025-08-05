package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AnimalManagementController implements IPortalAwareController {

    @FXML private TableView<Animal> animalTable;
    @FXML private TableColumn<Animal, String> idAdmissionDate;
    @FXML private TableColumn<Animal, String> nameColumn;
    @FXML private TableColumn<Animal, String> speciesColumn;
    @FXML private TableColumn<Animal, String> sexColumn;
    @FXML private TableColumn<Animal, String> adoptedColumn;
    @FXML private TableColumn<Animal, Void> actionsColumn;
    @FXML private Pagination pagination;
    @FXML private Button CreateAnimal;

    private PortalController portalController;
    private final int ROWS_PER_PAGE = 10;
    private List<Animal> allAnimals;

    @Override
    public void setPortalController(PortalController portalController) {
        this.portalController = portalController;
    }

    @FXML
    public void initialize(){
        try{
            // Initialize the animal table and pagination
            allAnimals = ServiceFactory.getAnimalService().getActiveAnimals();
            setUpTables(); //Set up the table columns and pagination
            addActionButtons();
            setUpPagination(); // Load the first page of animals
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
        // Load animals for the current page
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, allAnimals.size());
        List<Animal> pageAnimals = allAnimals.subList(fromIndex, toIndex);
        animalTable.getItems().setAll(pageAnimals);
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

    @FXML
    public void handleCreateAnimal() {
        if (portalController != null) {
            portalController.loadContent("/fxml/Animal/CreateAnimal.fxml");
        }
    }

    private void addActionButtons() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Editar");
            private final Button deleteBtn = new Button("Eliminar");
            private final Button detailBtn = new Button("Detalles");

            {
                editBtn.setTooltip(new Tooltip("Editar Animal"));
                deleteBtn.setTooltip(new Tooltip("Desactivar Animal"));
                detailBtn.setTooltip(new Tooltip("Ver Detalles"));

                editBtn.getStyleClass().add("edit-btn");
                deleteBtn.getStyleClass().add("delete-btn");
                detailBtn.getStyleClass().add("detail-btn");


                /**
                 * In this action buttons, we are not using the loadContent method, because we need to pass the object
                 * */
                editBtn.setOnAction(event -> {
                    Animal animal = getTableView().getItems().get(getIndex());
                    //TODO Open edit view for this animal, do it the same way as in CreateAnimalController
                });

                detailBtn.setOnAction(event -> {
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
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Error");
                            alert.setHeaderText("No se pudo cargar los detalles del animal");
                            alert.setContentText(e.getMessage());
                            alert.showAndWait();
                        }
                    }
                });

                deleteBtn.setOnAction(event -> {
                    Animal animal = getTableView().getItems().get(getIndex());
                    try {
                        ServiceFactory.getAnimalService().deleteAnimal(animal.getRecordNumber());
                        allAnimals = ServiceFactory.getAnimalService().getActiveAnimals();
                        setUpPagination();
                    } catch (Exception e) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("No se pudo eliminar el animal");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    editBtn.setDisable(true);
                    deleteBtn.setDisable(true);
                    detailBtn.setDisable(true);
                } else {
                    editBtn.setDisable(false);
                    deleteBtn.setDisable(false);
                    detailBtn.setDisable(false);

                    HBox buttonBox = new HBox(5, detailBtn, editBtn, deleteBtn);
                    buttonBox.setAlignment(Pos.CENTER);
                    setGraphic(buttonBox);
                }
            }
        });
    }


}
