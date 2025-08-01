package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.util.List;

public class AnimalManagementController {

    @FXML private TableView<Animal> animalTable;
    @FXML private TableColumn<Animal, Integer> idColumn;
    @FXML private TableColumn<Animal, String> nameColumn;
    @FXML private TableColumn<Animal, String> speciesColumn;
    @FXML private TableColumn<Animal, String> sexColumn;
    @FXML private TableColumn<Animal, String> adoptedColumn;
    @FXML private TableColumn<Animal, Void> actionsColumn;
    @FXML private Pagination pagination;

    private final int ROWS_PER_PAGE = 10;
    private List<Animal> allAnimals;


    public void Initialize() throws Exception {
        try{
            // Initialize the animal table and pagination
            allAnimals = ServiceFactory.getAnimalService().getActiveAnimals();

            //Set up the table columns and pagination
            setUpTables();
            addActionButtons();

            // Load the first page of animals
            setUpPagination();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void setUpTables() {
        // Initialize the table columns and pagination
        idColumn.setCellValueFactory(new PropertyValueFactory<>("recordNumber"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        speciesColumn.setCellValueFactory(new PropertyValueFactory<>("species"));
        sexColumn.setCellValueFactory(new PropertyValueFactory<>("sex"));
        adoptedColumn.setCellValueFactory(new PropertyValueFactory<>("adopted"));
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

    public void handleCreateAnimal(ActionEvent event) {

    }

    private void addActionButtons() {
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏️");
            private final Button deleteBtn = new Button("🗑️");

            {
                editBtn.setTooltip(new Tooltip("Editar Animal"));
                deleteBtn.setTooltip(new Tooltip("Desactivar Animal"));


                // Inline CSS styles
                editBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5; -fx-cursor: hand;");
                deleteBtn.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5; -fx-cursor: hand;");


                editBtn.setOnAction(event -> {
                    Animal animal = getTableView().getItems().get(getIndex());
                    //TODO Open edit form for this animal
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
                } else {
                    editBtn.setDisable(false);
                    deleteBtn.setDisable(false);
                    setGraphic(new HBox(5, editBtn, deleteBtn));
                }
            }
        });
    }


}
