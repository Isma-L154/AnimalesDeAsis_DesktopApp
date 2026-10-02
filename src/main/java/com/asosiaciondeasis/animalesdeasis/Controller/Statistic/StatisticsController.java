package com.asosiaciondeasis.animalesdeasis.Controller.Statistic;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsService;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Util.ScreenTasks;
import com.asosiaciondeasis.animalesdeasis.Util.Exporters.CsvStatisticsExporter;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.EmptyState;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.KpiCard;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class StatisticsController implements IPortalAwareController {
    private static final Logger log = LoggerFactory.getLogger(StatisticsController.class);

    private static final int MAX_ORIGINS_SHOWN = 10;
    private static final int MAX_ORIGIN_LABEL = 30;

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

    private final IStatisticsService statisticsService = ServiceFactory.getStatisticsService();
    private final CsvStatisticsExporter csvExporter = ServiceFactory.getCsvStatisticsExporter();
    private final ScreenTasks tasks = new ScreenTasks("statistics");
    private int currentYear;

    private Map<String, Integer> monthlyData = new LinkedHashMap<>();
    private Map<String, Integer> originsData = new LinkedHashMap<>();
    private int totalAdmissions;
    private double adoptionRate;

    /** Everything one refresh reads, handed to the interface thread in a single piece. */
    private record YearStatistics(Map<String, Integer> monthly, Map<String, Integer> origins,
                                  int totalAdmissions, double adoptionRate) {
    }

    @FXML
    public void initialize() {
        setupYearComboBox();
        updateTiles();
        setupCharts();
        refreshData();
    }

    @FXML
    private void onYearChanged() {
        Integer selectedYear = yearComboBox.getValue();
        if (selectedYear != null && selectedYear != currentYear) {
            currentYear = selectedYear;
            refreshData();
        }
    }

    /** Reloads the selected year's figures off the interface thread, then redraws. */
    @FXML
    public void refreshData() {
        setUIEnabled(false);
        updateStatus("Cargando datos...", false);

        int year = currentYear;
        tasks.submit(() -> new YearStatistics(
                        statisticsService.getMonthlyAdmissions(year),
                        statisticsService.getAnimalOrigins(year),
                        statisticsService.getTotalAdmissions(year),
                        statisticsService.getAdoptionRate(year)),
                stats -> {
                    monthlyData = stats.monthly();
                    originsData = stats.origins();
                    totalAdmissions = stats.totalAdmissions();
                    adoptionRate = stats.adoptionRate();

                    updateTiles();
                    updateCharts();
                    updateStatus("Datos cargados correctamente", true);
                    updateLastUpdateTime();
                    setUIEnabled(true);
                },
                cause -> {
                    log.error("Could not load statistics for {}", year, cause);
                    updateStatus("Error al cargar datos: " + cause.getMessage(), false);
                    NavigationHelper.showErrorAlert("Error", "Error al cargar datos", cause.getMessage());
                    setUIEnabled(true);
                });
    }

    /**
     * Exports the selected year to CSV. The destination is chosen here, on the
     * interface thread; the queries and the write run in the background.
     */
    @FXML
    private void exportToCSV() {
        int year = currentYear;
        File file = csvExporter.chooseFile(year, exportButton.getScene().getWindow());
        if (file == null) {
            updateStatus("Exportación cancelada", false);
            return;
        }

        setUIEnabled(false);
        updateStatus("Exportando datos...", false);

        tasks.submit(() -> {
                    csvExporter.exportToFile(file, year);
                    return null;
                },
                done -> {
                    updateStatus("Exportación completada", true);
                    NavigationHelper.showSuccessAlert("Éxito", "Exportación completada");
                    setUIEnabled(true);
                },
                cause -> {
                    log.error("Could not export statistics for {}", year, cause);
                    updateStatus("Error al exportar: " + cause.getMessage(), false);
                    NavigationHelper.showErrorAlert("Error", "Error al exportar datos", cause.getMessage());
                    setUIEnabled(true);
                });
    }

    /** Nothing here navigates; the portal reference is not needed. */
    @Override
    public void setPortalController(PortalController controller) {
    }

    /** Stops a pending load or export from redrawing this screen after it has been replaced. */
    @Override
    public void cleanup() {
        tasks.close();
    }

    /** Offers the last five years, with the current one selected. */
    private void setupYearComboBox() {
        ObservableList<Integer> years = FXCollections.observableArrayList();
        int thisYear = LocalDateTime.now().getYear();
        for (int i = thisYear; i >= thisYear - 4; i--) {
            years.add(i);
        }

        yearComboBox.setItems(years);
        yearComboBox.setValue(thisYear);
        currentYear = thisYear;
    }

    private void setupCharts() {
        try {
            monthsAxis.setLabel("Mes");
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
            log.error("Could not configure the charts", e);
        }
    }

    /**
     * Puts each chart in a stack with the message that replaces it when its year
     * has no records. Axes drawn around nothing, or a placeholder pie slice,
     * read as a result rather than as an absence.
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
     * Rebuilds the headline cards from the current figures. Cheaper than it
     * sounds, and it keeps them free of a "created empty, mutated later" state.
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

    private void updateCharts() {
        updateMonthlyChart();
        updateOriginsChart();
        updatePieChart();
    }

    private void updateMonthlyChart() {
        try {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Admisiones");

            // Abbreviated: twelve full Spanish names do not fit across the axis
            // and get drawn on top of one another.
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
            log.error("Could not update the monthly chart", e);
        }
    }

    /** Shows the busiest origins; the DAO returns them largest first. */
    private void updateOriginsChart() {
        try {
            XYChart.Series<Number, String> series = new XYChart.Series<>();
            series.setName("Origen");

            int maxValue = 0;
            for (Map.Entry<String, Integer> entry : originsData.entrySet()) {
                if (series.getData().size() == MAX_ORIGINS_SHOWN) {
                    break;
                }
                String origin = entry.getKey();
                int count = entry.getValue();
                maxValue = Math.max(maxValue, count);

                String displayName = origin.length() > MAX_ORIGIN_LABEL
                        ? origin.substring(0, MAX_ORIGIN_LABEL) + "..." : origin;
                series.getData().add(new XYChart.Data<>(count, displayName));
            }

            originsCountAxis.setLowerBound(0);
            originsCountAxis.setUpperBound(Math.max(maxValue + 1, 4));
            originsCountAxis.setTickUnit(Math.max(1, (maxValue + 1) / 5));

            originsChart.getData().clear();
            originsChart.getData().add(series);

            EmptyState.toggle(originsChart, originsEmpty, !series.getData().isEmpty());

        } catch (Exception e) {
            updateStatus("Error al actualizar gráfico de orígenes: " + e.getMessage(), false);
            log.error("Could not update the origins chart", e);
        }
    }

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
            log.error("Could not update the adoption chart", e);
        }
    }

    private void setUIEnabled(boolean enabled) {
        if (refreshButton != null) refreshButton.setDisable(!enabled);
        if (exportButton != null) exportButton.setDisable(!enabled);
        if (yearComboBox != null) yearComboBox.setDisable(!enabled);
    }

    private void updateStatus(String message, boolean success) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            // Style classes rather than setStyle: an inline style wins over any
            // stylesheet rule, which would put the palette in Java and out of
            // reach of the theme.
            statusLabel.getStyleClass().removeAll("status-success", "status-error");
            statusLabel.getStyleClass().add(success ? "status-success" : "status-error");
        }
    }

    private void updateLastUpdateTime() {
        if (lastUpdateLabel != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            lastUpdateLabel.setText("Última actualización: " + LocalDateTime.now().format(formatter));
        }
    }
}
