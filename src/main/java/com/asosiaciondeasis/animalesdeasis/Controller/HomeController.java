package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.Animal.DetailAnimalController;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.NavigationSection;
import com.asosiaciondeasis.animalesdeasis.Model.ShelterSummary;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import com.asosiaciondeasis.animalesdeasis.Util.SyncEventManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home panel: what the shelter looks like right now.
 *
 * <p>Replaces a 200px logo and the words "Bienvenido al panel de administración",
 * which spent the moment the application has someone's full attention saying
 * nothing.</p>
 *
 * <p><b>Nothing here touches the database on the interface thread.</b> The panel
 * needs six queries, and running them in {@code initialize()} — which is what
 * the animals screen used to do — freezes the window until they all return. The
 * layout is built empty with skeleton placeholders, a background task fetches
 * everything at once, and the results are applied in a single hop back.</p>
 */
public class HomeController implements IPortalAwareController {

    private static final int SKELETON_ROWS = 4;

    @FXML private Label greetingLabel;
    @FXML private Label subtitleLabel;
    @FXML private HBox kpiRow;
    @FXML private VBox recentPanel;
    @FXML private VBox attentionPanel;
    @FXML private HBox errorBanner;

    private PortalController portalController;
    private ExecutorService executor;
    private Runnable syncListener;

    @FXML
    public void initialize() {
        greetingLabel.setText(greeting());
        subtitleLabel.setText("Cargando el estado del albergue…");

        // A daemon thread: an in-flight query must never keep the application
        // alive after the window is closed.
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "home-panel");
            t.setDaemon(true);
            return t;
        });

        showSkeleton();

        // A completed sync can change every figure on this panel, so it reloads
        // rather than sitting on numbers that are quietly out of date.
        syncListener = () -> Platform.runLater(this::reload);
        SyncEventManager.addListener(syncListener);

        reload();
    }

    private String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) {
            return "Buenos días";
        }
        return hour < 19 ? "Buenas tardes" : "Buenas noches";
    }

    // -------------------------------------------------------------------------
    //  Loading
    // -------------------------------------------------------------------------

    private void reload() {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        Task<ShelterSummary> task = new Task<>() {
            @Override
            protected ShelterSummary call() throws Exception {
                return ServiceFactory.getShelterSummaryService().load();
            }
        };
        task.setOnSucceeded(e -> render(task.getValue()));
        task.setOnFailed(e -> showError(task.getException()));
        executor.submit(task);
    }

    private void render(ShelterSummary summary) {
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);

        subtitleLabel.setText(summary.inShelter() == 1
                ? "1 animal en el albergue"
                : summary.inShelter() + " animales en el albergue");

        kpiRow.getChildren().setAll(
                kpi("fas-home", "En el albergue", String.valueOf(summary.inShelter()),
                        null, false),
                kpi("fas-heart", "Adoptados " + summary.year(),
                        String.valueOf(summary.adoptedThisYear()),
                        String.format("%.0f%% de los atendidos", summary.adoptionRate()), false),
                kpi("fas-syringe", "Sin vacunas",
                        String.valueOf(summary.missingVaccines().size()),
                        summary.missingVaccines().isEmpty() ? "todo al día" : "requieren atención",
                        !summary.missingVaccines().isEmpty()));

        renderRecent(summary.recentAdmissions());
        renderAttention(summary);
    }

    private void renderRecent(List<Animal> animals) {
        recentPanel.getChildren().setAll(panelHeading("Últimos ingresos", NavigationSection.ANIMALS));
        if (animals.isEmpty()) {
            recentPanel.getChildren().add(emptyState(
                    "Todavía no hay animales registrados",
                    "Cuando registres el primero aparecerá acá."));
            return;
        }
        for (Animal animal : animals) {
            recentPanel.getChildren().add(animalRow(animal,
                    DateUtils.formatUtcForDisplay(animal.getAdmissionDate())));
        }
    }

    /**
     * The part that earns the panel its place. Counts alone are decoration people
     * learn to ignore, so every line here names the animals behind it and opens
     * the one you click.
     */
    private void renderAttention(ShelterSummary summary) {
        attentionPanel.getChildren().setAll(panelHeading("Requieren atención", null));

        if (!summary.hasAttentionItems()) {
            attentionPanel.getChildren().add(emptyState(
                    "Nada pendiente",
                    "Todos los registros están completos y sincronizados."));
            return;
        }

        if (!summary.missingVaccines().isEmpty()) {
            attentionPanel.getChildren().add(attentionGroup(
                    "Sin vacunas registradas", summary.missingVaccines()));
        }
        if (!summary.missingChip().isEmpty()) {
            attentionPanel.getChildren().add(attentionGroup(
                    "Sin número de chip", summary.missingChip()));
        }
        if (summary.pendingUpload() > 0) {
            Label pending = new Label(summary.pendingUpload() == 1
                    ? "1 registro pendiente de subir a la nube"
                    : summary.pendingUpload() + " registros pendientes de subir a la nube");
            pending.getStyleClass().add("attention-note");
            pending.setWrapText(true);
            attentionPanel.getChildren().add(pending);
        }
    }

    private VBox attentionGroup(String title, List<Animal> animals) {
        Label heading = new Label(title + " (" + animals.size() + ")");
        heading.getStyleClass().add("attention-group-title");

        VBox group = new VBox(4, heading);
        group.getStyleClass().add("attention-group");
        for (Animal animal : animals) {
            group.getChildren().add(animalRow(animal, "Abrir"));
        }
        return group;
    }

    // -------------------------------------------------------------------------
    //  Building blocks
    // -------------------------------------------------------------------------

    private VBox kpi(String icon, String caption, String value, String note, boolean warn) {
        FontIcon glyph = new FontIcon(icon);
        glyph.getStyleClass().add("kpi-icon");

        Label captionLabel = new Label(caption, glyph);
        captionLabel.getStyleClass().add("kpi-caption");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("kpi-value");

        VBox card = new VBox(2, captionLabel, valueLabel);
        card.getStyleClass().addAll("kpi-card", "surface-card");
        if (warn) {
            card.getStyleClass().add("kpi-warn");
        }
        if (note != null) {
            Label noteLabel = new Label(note);
            noteLabel.getStyleClass().add("kpi-note");
            card.getChildren().add(noteLabel);
        }
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private HBox panelHeading(String title, NavigationSection linkTo) {
        Label label = new Label(title);
        label.getStyleClass().add("panel-title");

        HBox heading = new HBox(label);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("panel-heading");

        if (linkTo != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button all = new Button("Ver todos");
            all.getStyleClass().add("panel-link");
            all.setOnAction(e -> {
                if (portalController != null) {
                    portalController.navigateTo(linkTo);
                }
            });
            heading.getChildren().addAll(spacer, all);
        }
        return heading;
    }

    /**
     * A row is a Button so it is reachable by keyboard and announces itself, the
     * same reasoning that took the navigation rail off Labels.
     */
    private Button animalRow(Animal animal, String trailing) {
        String name = (animal.getName() == null || animal.getName().isBlank())
                ? "Sin nombre" : animal.getName();
        String species = animal.getSpecies() == null ? "" : " · " + animal.getSpecies();

        Label main = new Label(name + species);
        main.getStyleClass().add("row-main");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label meta = new Label(trailing);
        meta.getStyleClass().add("row-meta");

        HBox content = new HBox(8, main, spacer, meta);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);

        Button row = new Button();
        row.setGraphic(content);
        row.getStyleClass().add("panel-row-button");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAccessibleText("Abrir la ficha de " + name);
        row.setOnAction(e -> openDetail(animal));
        return row;
    }

    private VBox emptyState(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("empty-detail");
        detailLabel.setWrapText(true);

        VBox box = new VBox(3, titleLabel, detailLabel);
        box.getStyleClass().add("empty-state");
        return box;
    }

    /** Grey bars in the shape of the content, so the panel has a form before it has data. */
    private void showSkeleton() {
        kpiRow.getChildren().setAll(skeletonCard(), skeletonCard(), skeletonCard());
        recentPanel.getChildren().setAll(skeletonRows());
        attentionPanel.getChildren().setAll(skeletonRows());
    }

    private VBox skeletonCard() {
        VBox card = new VBox(8, skeletonBar(0.5), skeletonBar(0.3));
        card.getStyleClass().addAll("kpi-card", "surface-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox skeletonRows() {
        VBox box = new VBox(9);
        box.getChildren().add(skeletonBar(0.4));
        for (int i = 0; i < SKELETON_ROWS; i++) {
            box.getChildren().add(skeletonBar(0.9));
        }
        return box;
    }

    private Region skeletonBar(double widthRatio) {
        Region bar = new Region();
        bar.getStyleClass().add("skeleton-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.scaleXProperty().set(1);
        VBox.setVgrow(bar, Priority.NEVER);
        bar.prefWidthProperty().bind(
                recentPanel.widthProperty().multiply(widthRatio));
        return bar;
    }

    private void openDetail(Animal animal) {
        if (portalController == null) {
            return;
        }
        try {
            // The class literal rather than getClass(): the latter resolves the path
            // against whatever the runtime type is, so a subclass in another
            // package would look for the resource somewhere else entirely.
            FXMLLoader loader = new FXMLLoader(
                    HomeController.class.getResource("/fxml/Animal/DetailAnimal.fxml"));
            Parent root = loader.load();
            DetailAnimalController detail = loader.getController();
            detail.setPortalController(portalController);
            detail.setAnimalDetails(animal, ServiceFactory.getPlaceService().getAllPlaces());
            portalController.setContent(root);
        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", "No se pudo abrir la ficha del animal",
                    e.getMessage());
        }
    }

    /**
     * A failure here is shown in the panel with a way to retry, not as a modal.
     * The home screen is where the application starts; a dialog that has to be
     * dismissed before anything can be seen is the wrong first thing to meet.
     */
    private void showError(Throwable cause) {
        subtitleLabel.setText("No se pudo cargar el estado del albergue");

        FontIcon icon = new FontIcon("fas-exclamation-triangle");
        icon.getStyleClass().add("banner-icon");

        Label message = new Label(cause == null ? "Error desconocido" : cause.getMessage());
        message.getStyleClass().add("banner-text");
        message.setWrapText(true);
        HBox.setHgrow(message, Priority.ALWAYS);

        Button retry = new Button("Reintentar");
        retry.getStyleClass().add("banner-action");
        retry.setOnAction(e -> {
            showSkeleton();
            reload();
        });

        errorBanner.getChildren().setAll(icon, message, retry);
        errorBanner.setAlignment(Pos.CENTER_LEFT);
        errorBanner.setVisible(true);
        errorBanner.setManaged(true);

        kpiRow.getChildren().clear();
        recentPanel.getChildren().clear();
        attentionPanel.getChildren().clear();
    }

    @Override
    public void setPortalController(PortalController portalController) {
        this.portalController = portalController;
    }

    @Override
    public void cleanup() {
        if (syncListener != null) {
            SyncEventManager.removeListener(syncListener);
            syncListener = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
