package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Config.Database;
import com.asosiaciondeasis.animalesdeasis.Config.SQLiteSetup;
import com.asosiaciondeasis.animalesdeasis.Controller.Animal.AnimalManagementController;
import com.asosiaciondeasis.animalesdeasis.Controller.Animal.CreateAnimalController;
import com.asosiaciondeasis.animalesdeasis.Controller.Animal.DetailAnimalController;
import com.asosiaciondeasis.animalesdeasis.Controller.Animal.EditAnimalController;
import com.asosiaciondeasis.animalesdeasis.Controller.Vaccine.VaccineManagementController;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.JavaFxToolkit;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import com.asosiaciondeasis.animalesdeasis.Util.SyncEventManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives the real portal - FXML, controllers, services and a SQLite database -
 * through the flows people use every day.
 *
 * <p>Controls are operated from the JavaFX thread ({@code fire()},
 * {@code setText()}) rather than by moving the mouse, so the suite runs on a
 * developer's machine without taking over the pointer. The database and the
 * interface preferences are redirected by the surefire configuration, so nothing
 * here touches a person's real records.</p>
 */
class ScreenFlowTest {

    private static final long TIMEOUT_MS = 10_000;

    private Stage stage;
    private Parent root;
    private PortalController portal;
    private final AnimalDAO animalDAO = new AnimalDAO(Database.dataSource());
    private final VaccineDAO vaccineDAO = new VaccineDAO(Database.dataSource());
    private int placeId;

    @BeforeAll
    static void startToolkit() throws Exception {
        assumeTrue(System.getProperty("animalesdeasis.data.dir") != null,
                "run through Maven: the surefire configuration redirects the database");
        Files.createDirectories(Path.of(System.getProperty("animalesdeasis.data.dir")));
        JavaFxToolkit.start();
    }

    @BeforeEach
    void openPortal() throws Exception {
        placeId = resetDatabase();
        JavaFxToolkit.onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(ScreenFlowTest.class.getResource("/fxml/PortalView.fxml"));
            root = loader.load();
            portal = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root));
        });
        // Lets the portal's deferred set-up (toast layer, shortcuts) run.
        JavaFxToolkit.onFxThread(() -> { });
    }

    @AfterEach
    void closePortal() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            portal.dispose();
            stage.close();
        });
        Preferences.userRoot().node(System.getProperty("animalesdeasis.prefs.node")).removeNode();
    }

    // -------------------------------------------------------------------------
    //  Flows
    // -------------------------------------------------------------------------

    @Test
    void theAnimalListShowsStoredAnimals() throws Exception {
        Animal stored = storedAnimal("Canela", null);
        JavaFxToolkit.onFxThread(() -> portal.loadContent("/fxml/Animal/AnimalManagement.fxml"));

        waitUntil("the list shows the stored animal", () -> {
            TableView<?> table = (TableView<?>) find("#animalTable");
            return table.getItems().stream()
                    .anyMatch(row -> ((Animal) row).getRecordNumber().equals(stored.getRecordNumber()));
        });
    }

    @Test
    void creatingAnAnimalSavesItAndReturnsToTheList() throws Exception {
        openCreateForm();
        fillCreateForm("Toño", null);

        JavaFxToolkit.onFxThread(() -> button("#saveButton").fire());

        waitUntil("the form returns to the list", () -> currentController() instanceof AnimalManagementController);
        assertTrue(animalDAO.getAllAnimals().stream().anyMatch(a -> "Toño".equals(a.getName())),
                "the new animal is stored, accented name included");
    }

    /** The regression behind #97: a duplicate chip was reported as saved. */
    @Test
    void aDuplicateChipIsMarkedOnTheFieldAndNothingIsSaved() throws Exception {
        storedAnimal("Canela", "CHIP-1");
        openCreateForm();
        fillCreateForm("Otro", "CHIP-1");

        JavaFxToolkit.onFxThread(() -> button("#saveButton").fire());

        waitUntil("the chip field is marked", () ->
                find("#chipNumberField").getStyleClass().contains("field-error"));
        assertInstanceOf(CreateAnimalController.class, currentController(), "the form stays open");
        assertEquals(1, animalDAO.getAllAnimals().size(), "nothing was saved");
    }

    @Test
    void editingAnAnimalSavesTheChange() throws Exception {
        Animal stored = storedAnimal("Canela", null);
        JavaFxToolkit.onFxThread(() -> portal.<EditAnimalController>openScreen("/fxml/Animal/EditAnimal.fxml",
                edit -> edit.setAnimalData(stored)));

        waitUntil("the stored place is selected once places load", () ->
                ((ComboBox<?>) find("#placeComboBox")).getValue() != null);
        JavaFxToolkit.onFxThread(() -> {
            ((TextField) find("#nameField")).setText("Canela Editada");
            button("#updateButton").fire();
        });

        waitUntil("the form returns to the list", () -> currentController() instanceof AnimalManagementController);
        assertEquals("Canela Editada", animalDAO.findByRecordNumber(stored.getRecordNumber()).getName());
    }

    @Test
    void aVaccineAddedThroughTheModalIsStoredAndListed() throws Exception {
        Animal stored = storedAnimal("Canela", null);
        AtomicReference<VaccineManagementController> screen = new AtomicReference<>();
        JavaFxToolkit.onFxThread(() -> portal.<VaccineManagementController>openScreen(
                "/fxml/Vaccine/VaccineManagement.fxml", vaccines -> {
                    vaccines.setCurrentAnimal(stored);
                    screen.set(vaccines);
                }));

        // Builds the skins, so the table has a scene for the modal to be owned by.
        JavaFxToolkit.onFxThread(() -> find("#vaccineTable"));
        // The modal blocks in showAndWait, so it is opened without waiting for it.
        Platform.runLater(() -> screen.get().onCreateNewVaccine());
        waitUntil("the modal opens", () -> modal("Nueva Vacuna") != null);
        JavaFxToolkit.onFxThread(() -> {
            Parent form = modal("Nueva Vacuna").getScene().getRoot();
            ((TextField) form.lookup("#vaccineNameField")).setText("Rabia");
            ((Button) form.lookup(".vaccine-create-save-button")).fire();
        });

        waitUntil("the table lists the vaccine", () -> {
            TableView<?> table = (TableView<?>) find("#vaccineTable");
            return table.getItems().size() == 1;
        });
        assertEquals("Rabia", vaccineDAO.getVaccinesByAnimal(stored.getRecordNumber()).get(0).getVaccineName());
    }

    /**
     * The regression behind #97: opening a detail screen replaced the content
     * without telling the list, which kept its sync listener forever.
     */
    @Test
    void leavingTheListReleasesItsSyncListener() throws Exception {
        Animal stored = storedAnimal("Canela", null);
        JavaFxToolkit.onFxThread(() -> portal.loadContent("/fxml/Animal/AnimalManagement.fxml"));
        int withList = SyncEventManager.listenerCount();

        JavaFxToolkit.onFxThread(() -> portal.<DetailAnimalController>openScreen("/fxml/Animal/DetailAnimal.fxml",
                detail -> detail.setAnimalDetails(stored)));

        assertEquals(withList - 1, SyncEventManager.listenerCount());
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private void openCreateForm() throws Exception {
        JavaFxToolkit.onFxThread(() -> portal.loadContent("/fxml/Animal/AnimalManagement.fxml"));
        JavaFxToolkit.onFxThread(() -> button("#CreateAnimal").fire());
        waitUntil("the create form opens", () -> currentController() instanceof CreateAnimalController);
        waitUntil("places load", () -> !((ComboBox<?>) find("#placeComboBox")).getItems().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private void fillCreateForm(String name, String chip) throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            ((TextField) find("#nameField")).setText(name);
            ((ComboBox<String>) find("#speciesComboBox")).setValue("Perro");
            ((ComboBox<String>) find("#sexComboBox")).setValue("Macho");
            ((Spinner<Integer>) find("#ageSpinner")).getValueFactory().setValue(2);
            ComboBox<Place> places = (ComboBox<Place>) find("#placeComboBox");
            places.setValue(places.getItems().get(0));
            ((TextField) find("#collectedByField")).setText("Ana");
            if (chip != null) {
                ((TextField) find("#chipNumberField")).setText(chip);
            }
        });
    }

    /**
     * Looks a control up by selector. The forms sit inside ScrollPanes, whose
     * content joins the scene graph only once a skin exists; applying CSS
     * creates the skins without having to show the window.
     */
    private Node find(String selector) {
        root.applyCss();
        root.layout();
        return root.lookup(selector);
    }

    private Button button(String selector) {
        Node node = find(selector);
        assertNotNull(node, "no control matches " + selector);
        return (Button) node;
    }

    /** The controller of the screen currently in the portal's centre. */
    private Object currentController() {
        return ((Parent) find("#contentPane")).getChildrenUnmodifiable().stream()
                .map(Node::getUserData)
                .filter(data -> data != null)
                .findFirst()
                .orElse(null);
    }

    private static Stage modal(String title) {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage s && title.equals(s.getTitle()) && s.isShowing())
                .map(window -> (Stage) window)
                .findFirst()
                .orElse(null);
    }

    /** Polls {@code condition} on the JavaFX thread until it holds. */
    private static void waitUntil(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        AtomicReference<Boolean> holds = new AtomicReference<>(false);
        while (System.currentTimeMillis() < deadline) {
            JavaFxToolkit.onFxThread(() -> holds.set(condition.call()));
            if (holds.get()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting until " + what);
    }

    private Animal storedAnimal(String name, String chip) throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setName(name);
        animal.setChipNumber(chip);
        animal.setCollectedBy("Ana");
        animal.setAdmissionDate("2024-01-15T00:00:00");
        animalDAO.insertAnimal(animal);
        return animalDAO.findByRecordNumber(animal.getRecordNumber());
    }

    /** Empties the scratch database and gives it one place to rescue animals from. */
    private static int resetDatabase() throws Exception {
        try (Connection conn = Database.dataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            SQLiteSetup.createSchema(conn);
            stmt.executeUpdate("DELETE FROM vaccines");
            stmt.executeUpdate("DELETE FROM deleted_vaccines");
            stmt.executeUpdate("DELETE FROM animals");
            stmt.executeUpdate("DELETE FROM places");
            stmt.executeUpdate("DELETE FROM provinces");
            return TestSupport.seedPlace(conn);
        }
    }

    @Test
    void theScratchDatabaseIsNotTheRealOne() {
        assertFalse(System.getProperty("animalesdeasis.data.dir").contains(".asociaciondeasis"),
                "these tests must never run against a person's records");
    }
}
