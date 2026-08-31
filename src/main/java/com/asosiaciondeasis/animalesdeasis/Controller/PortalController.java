package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.UiPreferences;
import com.asosiaciondeasis.animalesdeasis.Model.NavigationSection;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;

/**
 * The application shell: navigation rail on the left, header on top, and the
 * current screen in the centre.
 */
public class PortalController {

    /** Horizontal overhang of the collapse button, half its width. */
    private static final double COLLAPSE_BUTTON_OVERHANG = -13;

    @FXML private BorderPane mainPortal;
    @FXML private HBox headerInclude;
    @FXML private VBox sidebarInclude;
    @FXML private StackPane contentPane;

    private final SidebarController sidebar = new SidebarController();
    private Label sectionTitle;
    private SyncStatusIndicator syncIndicator;
    private Button collapseButton;
    private FontIcon collapseIcon;
    private NavigationSection currentSection;

    @FXML
    public void initialize() {
        headerInclude = (HBox) mainPortal.getTop();
        contentPane = (StackPane) mainPortal.getCenter();

        sectionTitle = (Label) headerInclude.lookup("#sectionTitle");

        Label syncChip = (Label) headerInclude.lookup("#syncStatus");
        if (syncChip != null) {
            syncIndicator = new SyncStatusIndicator(syncChip);
        }

        sidebar.setCollapsed(UiPreferences.isRailCollapsed());
        VBox rail = sidebar.build(this::navigateTo);
        sidebarInclude.getChildren().setAll(rail.getChildren());
        sidebarInclude.getStyleClass().setAll(rail.getStyleClass());
        applyRailWidth(sidebar.isCollapsed());

        buildCollapseButton();

        // Reopen wherever the user was. An unknown or removed id falls back
        // rather than failing, so a section renamed between versions does not
        // leave someone with an application that opens on nothing.
        NavigationSection restored = NavigationSection
                .byId(UiPreferences.getLastSection(NavigationSection.ANIMALS.id()))
                .orElse(NavigationSection.ANIMALS);
        navigateTo(restored);

        // Accelerators need a scene, which does not exist during initialize().
        Platform.runLater(this::installAccelerators);
    }

    /**
     * The collapse control: a circular button straddling the boundary between
     * rail and content.
     *
     * <p>It is a child of the content {@code StackPane}, aligned top-left and
     * pushed left by half its width so it overhangs the rail. Placing it in the
     * content rather than in the rail is what makes it work: as the last child of
     * the pane that renders above the rail, it is never painted over. Put inside
     * the rail, the content would cover the half that overhangs.</p>
     */
    private void buildCollapseButton() {
        collapseIcon = new FontIcon(sidebar.isCollapsed() ? "fas-chevron-right" : "fas-chevron-left");
        collapseIcon.getStyleClass().add("collapse-icon");

        collapseButton = new Button();
        collapseButton.setGraphic(collapseIcon);
        collapseButton.getStyleClass().add("collapse-button");
        collapseButton.setFocusTraversable(true);
        collapseButton.setOnAction(e -> toggleSidebar());
        collapseButton.setTranslateX(COLLAPSE_BUTTON_OVERHANG);
        collapseButton.setTranslateY(16);
        updateCollapseAffordance();

        StackPane.setAlignment(collapseButton, Pos.TOP_LEFT);
        contentPane.getChildren().add(collapseButton);
    }

    private void installAccelerators() {
        Scene scene = mainPortal.getScene();
        if (scene == null) {
            return;
        }
        // Ctrl+B is the conventional shortcut for toggling a side panel, and it
        // gives the collapse control a keyboard route that does not depend on
        // tabbing to a button that may be off to one side.
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN),
                this::toggleSidebar);
    }

    @FXML
    public void toggleSidebar() {
        boolean collapsed = !sidebar.isCollapsed();
        sidebar.setCollapsed(collapsed);
        applyRailWidth(collapsed);
        collapseIcon.setIconLiteral(collapsed ? "fas-chevron-right" : "fas-chevron-left");
        updateCollapseAffordance();
        UiPreferences.setRailCollapsed(collapsed);
    }

    private void updateCollapseAffordance() {
        String action = sidebar.isCollapsed() ? "Expandir menú" : "Contraer menú";
        collapseButton.setAccessibleText(action);
        collapseButton.setTooltip(new Tooltip(action + " (Ctrl+B)"));
    }

    /**
     * The rail sets its own width; the container that holds it has to be told the
     * same, or the layout keeps the old column and leaves a gap.
     */
    private void applyRailWidth(boolean collapsed) {
        double width = collapsed ? 72 : 180;
        sidebarInclude.setPrefWidth(width);
        sidebarInclude.setMinWidth(width);
        sidebarInclude.setMaxWidth(width);
    }

    /** Opens a section, records it, and highlights it in the rail. */
    public void navigateTo(NavigationSection section) {
        if (!loadContent(section.fxmlPath())) {
            return;
        }
        currentSection = section;
        sidebar.markActive(section);
        if (sectionTitle != null) {
            sectionTitle.setText(section.label());
        }
        UiPreferences.setLastSection(section.id());
    }

    public NavigationSection getCurrentSection() {
        return currentSection;
    }

    /**
     * Replaces the centre content, giving the outgoing screen a chance to release
     * what it holds.
     *
     * <p>The cleanup call used to be guarded by
     * {@code instanceof AnimalManagementController}, so exactly one screen was
     * ever told it was going away and every other one leaked its sync listener on
     * each navigation. It is part of {@link IPortalAwareController} now, so a new
     * screen cannot be left out by omission.</p>
     *
     * @return whether the content was loaded
     */
    public boolean loadContent(String fxmlPath) {
        cleanUpCurrentScreen();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent content = loader.load();

            Object controller = loader.getController();
            if (controller instanceof IPortalAwareController portalAware) {
                portalAware.setPortalController(this);
            }
            content.setUserData(controller);

            StackPane.setAlignment(content, Pos.CENTER);
            // Replace only the screen. The collapse button is a permanent child of
            // this pane and has to survive, so it is re-added on top.
            contentPane.getChildren().removeIf(node -> node != collapseButton);
            contentPane.getChildren().add(0, content);
            return true;
        } catch (IOException e) {
            NavigationHelper.showErrorAlert("Error", "No se pudo cargar el contenido",
                    "Error al cargar el archivo FXML: " + fxmlPath + "\n" + e.getMessage());
            return false;
        }
    }

    private void cleanUpCurrentScreen() {
        for (javafx.scene.Node node : contentPane.getChildren()) {
            if (node == collapseButton) {
                continue;
            }
            if (node.getUserData() instanceof IPortalAwareController controller) {
                try {
                    controller.cleanup();
                } catch (Exception e) {
                    // A screen that fails to tidy up must not block the one
                    // replacing it; the alternative is an application that cannot
                    // navigate away from a broken screen.
                    System.out.println("Error during screen cleanup: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Releases what the shell itself holds. Called when the window closes; the
     * synchronisation indicator keeps a listener on a static registry and a
     * polling thread, neither of which ends on its own.
     */
    public void dispose() {
        cleanUpCurrentScreen();
        if (syncIndicator != null) {
            syncIndicator.dispose();
        }
    }

    public void setContent(Parent node) {
        contentPane.getChildren().removeIf(child -> child != collapseButton);
        contentPane.getChildren().add(0, node);
    }
}
