package com.asosiaciondeasis.animalesdeasis.Controller.Statistic;

import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Service.Statistics.StatisticsService;
import com.asosiaciondeasis.animalesdeasis.Util.Exporters.CsvStatisticsExporter;

import com.asosiaciondeasis.animalesdeasis.Util.Helpers.EmptyState;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.KpiCard;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javafx.stage.Window;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(StatisticsController.class);


    // FXML Components
    @FXML private ComboBox<Integer> yearComboBox;
    @FXML private Button refreshButton;
    @FXML private Button exportButton;
    @FXML private HBox tilesContainer;
    @FXML private BarChart<String, Number> monthlyAdmissionsChart;
    @FXML private CategoryAxis monthsAxis;
    @FXML private NumberAxis admissionsAxis;
    @FXML private PieChart adoptionPieChart;
    @FXML private BarChart<Number, String> originsChart;
    @FXML private CategoryAxis originsAxis;
    @FXML private NumberAxis originsCountAxis;
    @FXML private Label statusLabel;
    @FXML private Label lastUpdateLabel;

    /** Empty states standing in for each chart when its year has no records. */
    private VBox monthlyEmpty;
    private VBox originsEmpty;
    private VBox pieEmpty;

    // Services
    private StatisticsService statisticsService;
    private CsvStatisticsExporter csvExporter;
    private int currentYear;

    // Data storage
    private Map<String, Integer> monthlyData = new LinkedHashMap<>();
    private Map<String, Integer> originsData = new LinkedHashMap<>();
    private int totalAdmissions;
    private double adoptionRate;

    @Override
    /**
     * Initializes the controller, sets up services, year selection, tiles, and charts,
     * and loads the initial data with a slight delay for UI readiness.
     *
     * @param url Not used.
     * @param resourceBundle Not used.
     */
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeServices();

        setupYearComboBox();
        setupTiles();
        setupCharts();
        Timeline initialLoadTimeline = new Timeline(new KeyFrame(Duration.millis(300), e -> {
            loadInitialData();
            updateLastUpdateTime();
        }));
        initialLoadTimeline.play();
    }
    /**
     * Initializes the services required for this controller.
     * This method is called during the initialization phase to set up the necessary services.
     */
    private void initializeServices() {
        try {
            this.statisticsService = ServiceFactory.getStatisticsService();
            this.csvExporter = ServiceFactory.getCsvStatisticsExporter();

        } catch (Exception e) {
            updateStatus("Error al inicializar servicios: " + e.getMessage(), false);
            log.error("Unexpected error", e);
        }
    }

    /**
     * Handles the event when the year selection changes in the ComboBox.
     * If a new year is selected, updates the current year and refreshes the data.
     */
    @FXML
    private void onYearChanged() {
        Integer selectedYear = yearComboBox.getValue();
        if (selectedYear != null && selectedYear != currentYear) {
            currentYear = selectedYear;
            refreshData();
        }
    }

    /**
     * Refreshes all statistical data by fetching it from the service asynchronously.
     * Updates the UI components (tiles, charts, labels) with the new data.
     * Handles errors and disables/enables UI controls during the process.
     */
    @FXML
    public void refreshData() {
        if (statisticsService == null) {
            updateStatus("Error: Servicios no inicializados", false);
            return;
        }
        setUIEnabled(false);
        updateStatus("Cargando datos...", false);

        Task<Void> loadDataTask = new Task<Void>() {
            private Map<String, Integer> taskMonthlyData;
            private Map<String, Integer> taskOriginsData;
            private int taskTotalAdmissions;
            private double taskAdoptionRate;

            @Override
            protected Void call() throws Exception {
                try {
                    taskMonthlyData = statisticsService.getMonthlyAdmissions(currentYear);
                    taskTotalAdmissions = statisticsService.getTotalAdmissions(currentYear);
                    taskAdoptionRate = statisticsService.getAdoptionRate(currentYear);
                    taskOriginsData = statisticsService.getAnimalOrigins(currentYear);

                    Platform.runLater(() -> {
                        monthlyData = taskMonthlyData != null ? taskMonthlyData : new LinkedHashMap<>();
                        originsData = taskOriginsData != null ? taskOriginsData : new LinkedHashMap<>();
                        totalAdmissions = taskTotalAdmissions;
                        adoptionRate = taskAdoptionRate;

                        updateTiles();
                        updateCharts();
                        updateStatus("Datos cargados correctamente", true);
                        updateLastUpdateTime();
                        setUIEnabled(true);
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        updateStatus("Error al cargar datos: " + e.getMessage(), false);
                        NavigationHelper.showErrorAlert("Error", "Error al cargar datos", e.getMessage());
                        setUIEnabled(true);
                    });
                    throw e;
                }
                return null;
            }
        };

        Thread loadThread = new Thread(loadDataTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Exports the current statistics to a CSV file using the CsvStatisticsExporter.
     * Handles UI state and error reporting during the export process.
     */
    @FXML
    private void exportToCSV() {
        if (csvExporter == null) {
            updateStatus("Error: Exportador no inicializado", false);
            return;
        }

        setUIEnabled(false);
        updateStatus("Exportando datos...", false);

        Task<Void> exportTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Platform.runLater(() -> {
                        try {
                            Window window = exportButton.getScene().getWindow();
                            boolean exported = csvExporter.export(currentYear, window);

                            if (exported) {
                                updateStatus("Exportación completada", true);
                                NavigationHelper.showSuccessAlert("Éxito", "Exportación completada");
                            } else {
                                updateStatus("Exportación cancelada", false);
                            }
                        } catch (Exception e) {
                            updateStatus("Error al exportar: " + e.getMessage(), false);
                            NavigationHelper.showErrorAlert("Error", "Error al exportar datos", e.getMessage());
                        } finally {
                            setUIEnabled(true);
                        }
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        updateStatus("Error al exportar: " + e.getMessage(), false);
                        setUIEnabled(true);
                    });
                    throw e;
                }
                return null;
            }
        };

        Thread exportThread = new Thread(exportTask);
        exportThread.setDaemon(true);
        exportThread.start();
    }

    /**
     * Loads the initial data for the current year.
     * This is called once after the controller is initialized.
     */
    private void loadInitialData() {
        refreshData();
    }

    /**
     * Sets up the year ComboBox with the last five years and selects the current year by default.
     */
    private void setupYearComboBox() {
        ObservableList<Integer> years = FXCollections.observableArrayList();
        int currentYear = LocalDateTime.now().getYear();
        //Show only the last 5 years
        for (int i = currentYear; i >= currentYear - 4; i--) {
            years.add(i);
        }

        yearComboBox.setItems(years);
        yearComboBox.setValue(currentYear);
        this.currentYear = currentYear;
    }

    /**
     * Configures and creates the TilesFX tiles for total admissions, adoption rate, and monthly average.
     * Adds the tiles to the tiles container in the UI.
     */
    /**
     * Nothing to build up front: the cards are rebuilt from the current figures
     * each time data arrives, which is cheaper than it sounds and removes the
     * "created empty, mutated later" split that TilesFX required.
     */
    private void setupTiles() {
        updateTiles();
    }

    /**
     * Configures the charts (monthly admissions, origins, and adoption pie chart) with labels and properties.
     */
    private void setupCharts() {
        try {
            monthsAxis.setLabel("Mes");
            // Three-letter months. Twelve full Spanish names - "Septiembre",
            // "Noviembre", "Diciembre" - do not fit across this axis and were
            // drawn on top of one another in a heap at the left edge. Rotating
            // them would keep them legible but cost vertical space on a chart
            // that has little; abbreviating costs nothing, because the axis is
            // labelled "Mes" and the order is obvious.
            monthsAxis.setTickLabelRotation(0);
            monthsAxis.setTickLabelGap(4);

            admissionsAxis.setLabel("Admisiones");
            admissionsAxis.setTickUnit(1);
            admissionsAxis.setMinorTickVisible(false);
            admissionsAxis.setAutoRanging(false);
            admissionsAxis.setForceZeroInRange(true);
            monthlyAdmissionsChart.setTitle("");
            monthlyAdmissionsChart.setLegendVisible(false);
            // Bars sized so twelve of them read as a series rather than as twelve
            // separate blocks with gaps wider than the data.
            monthlyAdmissionsChart.setBarGap(2);
            monthlyAdmissionsChart.setCategoryGap(8);

            originsAxis.setLabel("");
            originsCountAxis.setLabel("Cantidad de animales");
            originsCountAxis.setTickUnit(1);
            originsCountAxis.setMinorTickVisible(false);
            originsCountAxis.setAutoRanging(false);
            originsCountAxis.setForceZeroInRange(true);
            originsChart.setTitle("");
            originsChart.setLegendVisible(false);
            originsChart.setBarGap(2);
            originsChart.setCategoryGap(10);

            adoptionPieChart.setTitle("");
            adoptionPieChart.setLegendVisible(true);
            adoptionPieChart.setLabelsVisible(true);

            installEmptyStates();

        } catch (Exception e) {
            updateStatus("Error al configurar gráficos: " + e.getMessage(), false);
            log.error("No se pudieron configurar los gráficos", e);
        }
    }

    /**
     * Puts each chart in a stack with the message that replaces it when its year
     * has no records.
     *
     * <p>Selecting an empty year used to draw three sets of axes around nothing,
     * and the pie chart went further: it inserted a slice called "Sin datos" with
     * a value of 1, producing a full green circle. That reads as a result — one
     * category, a hundred percent of it — rather than as an absence.</p>
     */
    private void installEmptyStates() {
        monthlyEmpty = EmptyState.create("fas-chart-bar", "Sin admisiones este año",
                "Cuando se registren animales con fecha de ingreso en " + currentYear
                        + ", aparecerán acá mes a mes.");
        originsEmpty = EmptyState.create("fas-map-marker-alt", "Sin lugares registrados",
                "El origen se toma del lugar de rescate de cada animal.");
        pieEmpty = EmptyState.create("fas-chart-pie", "Sin adopciones que mostrar",
                "La proporción aparece cuando hay animales admitidos en el año.");

        replaceWithStack(monthlyAdmissionsChart, monthlyEmpty);
        replaceWithStack(originsChart, originsEmpty);
        replaceWithStack(adoptionPieChart, pieEmpty);
    }

    /** Swaps a chart for a stack holding the chart and its empty state. */
    private void replaceWithStack(Node chart, Node empty) {
        if (!(chart.getParent() instanceof VBox parent)) {
            return;
        }
        int index = parent.getChildren().indexOf(chart);
        if (index < 0) {
            return;
        }
        parent.getChildren().remove(index);
        parent.getChildren().add(index, EmptyState.wrap(chart, empty));
    }

    /**
     * Updates the values displayed in the TilesFX tiles based on the latest data.
     */
    private void updateTiles() {
        double monthlyAverage = monthlyData.isEmpty() ? 0
                : monthlyData.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);

        tilesContainer.getChildren().setAll(
                KpiCard.create("fas-clipboard-list", "Total de admisiones",
                        String.valueOf(totalAdmissions),
                        "en " + currentYear, false),
                KpiCard.create("fas-heart", "Tasa de adopción",
                        String.format("%.1f%%", adoptionRate),
                        totalAdmissions == 0 ? "sin datos para calcularla" : "de los admitidos", false),
                KpiCard.create("fas-calendar-alt", "Promedio mensual",
                        String.format("%.1f", monthlyAverage),
                        "admisiones por mes", false));
    }

    /**
     * Updates all charts (monthly admissions, origins, and adoption pie chart) with the latest data.
     */
    private void updateCharts() {
        updateMonthlyChart();
        updateOriginsChart();
        updatePieChart();
    }

    /**
     * Updates the monthly admissions bar chart with the latest monthly data.
     */
    private void updateMonthlyChart() {
        try {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Admisiones");

            // Abbreviated. Twelve full names did not fit and were drawn on top of
            // one another in a heap at the left edge of the axis.
            String[] monthNames = {
                    "Ene", "Feb", "Mar", "Abr", "May", "Jun",
                    "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
            };

            int maxValue = 0;
            int total = 0;
            for (int i = 1; i <= 12; i++) {
                String monthKey = String.format("%02d", i);
                int value = monthlyData.getOrDefault(monthKey, 0);
                maxValue = Math.max(maxValue, value);
                total += value;
                series.getData().add(new XYChart.Data<>(monthNames[i - 1], value));
            }

            // Headroom of one above the tallest bar, and never a scale so short
            // that a single admission fills the chart.
            admissionsAxis.setLowerBound(0);
            admissionsAxis.setUpperBound(Math.max(maxValue + 1, 4));
            admissionsAxis.setTickUnit(Math.max(1, (maxValue + 1) / 5));

            monthlyAdmissionsChart.getData().clear();
            monthlyAdmissionsChart.getData().add(series);

            EmptyState.toggle(monthlyAdmissionsChart, monthlyEmpty, total > 0);

        } catch (Exception e) {
            updateStatus("Error al actualizar gráfico mensual: " + e.getMessage(), false);
            log.error("Unexpected error", e);
        }
    }

    /**
     * Updates the origins bar chart with the latest origins data.
     */
    private void updateOriginsChart() {
        try {
            XYChart.Series<Number, String> series = new XYChart.Series<>();
            series.setName("Origen");

            final int[] maxValue = {0};

            originsData.entrySet().stream()
                    .limit(10)
                    .forEach(entry -> {
                        String origin = entry.getKey();
                        Integer count = entry.getValue();
                        maxValue[0] = Math.max(maxValue[0], count);

                        String displayName = origin.length() > 30 ? origin.substring(0, 30) + "..." : origin;
                        series.getData().add(new XYChart.Data<>(count, displayName));
                    });

            originsCountAxis.setLowerBound(0);
            originsCountAxis.setUpperBound(Math.max(maxValue[0] + 1, 4));
            originsCountAxis.setTickUnit(Math.max(1, (maxValue[0] + 1) / 5));

            originsChart.getData().clear();
            originsChart.getData().add(series);

            EmptyState.toggle(originsChart, originsEmpty, !series.getData().isEmpty());

        } catch (Exception e) {
            updateStatus("Error al actualizar gráfico de orígenes: " + e.getMessage(), false);
            log.error("Unexpected error", e);
        }
    }

    /**
     * Updates the adoption pie chart with the latest adoption rate and admissions data.
     */
    private void updatePieChart() {
        try {
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

            if (totalAdmissions > 0) {
                int adopted = (int) Math.round(totalAdmissions * adoptionRate / 100.0);
                int notAdopted = totalAdmissions - adopted;

                if (adopted > 0) {
                    pieChartData.add(new PieChart.Data("Adoptados (" + adopted + ")", adopted));
                }
                if (notAdopted > 0) {
                    pieChartData.add(new PieChart.Data("En el albergue (" + notAdopted + ")", notAdopted));
                }
            }
            // No placeholder slice. A "Sin datos" wedge of value 1 drew a full
            // circle, which reads as a complete result rather than an absence.
            adoptionPieChart.setData(pieChartData);

            EmptyState.toggle(adoptionPieChart, pieEmpty, !pieChartData.isEmpty());

        } catch (Exception e) {
            updateStatus("Error al actualizar gráfico circular: " + e.getMessage(), false);
            log.error("Unexpected error", e);
        }
    }

    /**
     * Enables or disables the main UI controls (refresh, export, year selection).
     *
     * @param enabled true to enable controls, false to disable.
     */
    private void setUIEnabled(boolean enabled) {
        if (refreshButton != null) refreshButton.setDisable(!enabled);
        if (exportButton != null) exportButton.setDisable(!enabled);
        if (yearComboBox != null) yearComboBox.setDisable(!enabled);
    }

    /**
     * Updates the status label with a message and color indicating success or error.
     *
     * @param message The status message to display.
     * @param success true for success (green), false for error (red).
     */
    private void updateStatus(String message, boolean success) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            // Style classes rather than setStyle: an inline style wins over any
            // stylesheet rule, so the old version could not be re-themed and had
            // to repeat the palette in Java.
            statusLabel.getStyleClass().removeAll("status-success", "status-error");
            statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
        }
    }

    /**
     * Updates the label showing the last time the data was updated.
     */
    private void updateLastUpdateTime() {
        if (lastUpdateLabel != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            lastUpdateLabel.setText("Última actualización: " + LocalDateTime.now().format(formatter));
        }
    }
}
