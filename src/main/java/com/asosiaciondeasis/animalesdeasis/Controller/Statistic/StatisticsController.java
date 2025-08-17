package com.asosiaciondeasis.animalesdeasis.Controller.Statistic;

import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Service.Statistics.StatisticsService;
import com.asosiaciondeasis.animalesdeasis.Util.Exporters.CsvStatisticsExporter;

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
import javafx.scene.layout.FlowPane;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StatisticsController implements Initializable {

    // FXML Components
    @FXML private ComboBox<Integer> yearComboBox;
    @FXML private Button refreshButton;
    @FXML private Button exportButton;
    @FXML private FlowPane tilesContainer;
    @FXML private BarChart<String, Number> monthlyAdmissionsChart;
    @FXML private CategoryAxis monthsAxis;
    @FXML private NumberAxis admissionsAxis;
    @FXML private PieChart adoptionPieChart;
    @FXML private BarChart<Number, String> originsChart;
    @FXML private CategoryAxis originsAxis;
    @FXML private NumberAxis originsCountAxis;
    @FXML private Label statusLabel;
    @FXML private Label lastUpdateLabel;

    // TilesFX Tiles
    private Tile totalAdmissionsTile;
    private Tile adoptionRateTile;
    private Tile monthlyAverageTile;

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
            e.printStackTrace();
        }
    }

    @FXML
    private void onYearChanged() {
        Integer selectedYear = yearComboBox.getValue();
        if (selectedYear != null && selectedYear != currentYear) {
            currentYear = selectedYear;
            refreshData();
        }
    }

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

    private void loadInitialData() {
        refreshData();
    }

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

    private void setupTiles() {
        try {
            // Total Admissions Tile
            totalAdmissionsTile = TileBuilder.create()
                    .skinType(Tile.SkinType.NUMBER)
                    .prefSize(250, 150)
                    .title("Total de Admisiones")
                    .textColor(Color.WHITE)
                    .backgroundColor(Color.web("#3498db"))
                    .foregroundColor(Color.WHITE)
                    .value(0)
                    .decimals(0)
                    .animated(true)
                    .textSize(Tile.TextSize.BIGGER)
                    .build();


            // Adoption Rate Tile
            adoptionRateTile = TileBuilder.create()
                    .skinType(Tile.SkinType.PERCENTAGE)
                    .prefSize(250, 150)
                    .title("Tasa de Adopción")
                    .textColor(Color.WHITE)
                    .backgroundColor(Color.web("#27ae60"))
                    .foregroundColor(Color.WHITE)
                    .unitColor(Color.WHITE)
                    .barColor(Color.WHITESMOKE)
                    .thresholdColor(Color.WHITE)
                    .value(0)
                    .decimals(1)
                    .animated(true)
                    .textSize(Tile.TextSize.BIGGER)
                    .build();

            // Monthly Average Tile
            monthlyAverageTile = TileBuilder.create()
                    .skinType(Tile.SkinType.NUMBER)
                    .prefSize(250, 150)
                    .title("Promedio Mensual")
                    .textColor(Color.WHITE)
                    .backgroundColor(Color.web("#f39c12"))
                    .foregroundColor(Color.WHITE)
                    .value(0)
                    .decimals(1)
                    .animated(true)
                    .textSize(Tile.TextSize.BIGGER)
                    .build();


            // Add tiles to container
            tilesContainer.getChildren().addAll(
                    totalAdmissionsTile,
                    adoptionRateTile,
                    monthlyAverageTile
            );

        } catch (Exception e) {
            updateStatus("Error al configurar tiles: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    private void setupCharts() {
        try {
            // Configure monthly admissions chart
            monthsAxis.setLabel("Mes");
            admissionsAxis.setLabel("Número de Admisiones");
            admissionsAxis.setTickUnit(1);
            admissionsAxis.setMinorTickVisible(false);
            admissionsAxis.setAutoRanging(false);
            monthlyAdmissionsChart.setTitle("");
            monthlyAdmissionsChart.setAnimated(true);
            monthlyAdmissionsChart.setLegendVisible(false);

            // Configure origins chart
            originsAxis.setLabel("Origen");
            originsCountAxis.setLabel("Cantidad de Animales");
            originsCountAxis.setTickUnit(1);
            originsCountAxis.setMinorTickVisible(false);
            originsCountAxis.setAutoRanging(false);
            originsCountAxis.setForceZeroInRange(true);
            originsChart.setTitle("");
            originsChart.setAnimated(true);
            originsChart.setLegendVisible(false);

            // Configure pie chart
            adoptionPieChart.setTitle("");
            adoptionPieChart.setAnimated(true);
            adoptionPieChart.setLegendVisible(true);

        } catch (Exception e) {
            updateStatus("Error al configurar gráficos: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

    private void updateTiles() {
        try {
            if (totalAdmissionsTile != null) {
                totalAdmissionsTile.setValue(totalAdmissions);
            }

            if (adoptionRateTile != null) {
                adoptionRateTile.setValue(adoptionRate);
            }

            if (monthlyAverageTile != null) {
                double monthlyAverage = monthlyData.isEmpty() ? 0 :
                        monthlyData.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
                monthlyAverageTile.setValue(monthlyAverage);
            }

        } catch (Exception e) {
            updateStatus("Error al actualizar tiles: " + e.getMessage(), false);
            e.printStackTrace();
        }
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

            String[] monthNames = {
                    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
            };

            int maxValue = 0;
            for (int i = 1; i <= 12; i++) {
                String monthKey = String.format("%02d", i);
                int value = monthlyData.getOrDefault(monthKey, 0);
                maxValue = Math.max(maxValue, value);
                String monthName = monthNames[i - 1];
                series.getData().add(new XYChart.Data<>(monthName, value));
            }

            admissionsAxis.setLowerBound(0);
            admissionsAxis.setUpperBound(Math.max(maxValue + 1, 5));

            monthlyAdmissionsChart.getData().clear();
            monthlyAdmissionsChart.getData().add(series);

        } catch (Exception e) {
            updateStatus("Error al actualizar gráfico mensual: " + e.getMessage(), false);
            e.printStackTrace();
        }
    }

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
            originsCountAxis.setUpperBound(Math.max(maxValue[0] + 1, 5));

            originsChart.getData().clear();
            originsChart.getData().add(series);

        } catch (Exception e) {
            updateStatus("Error al actualizar gráfico de orígenes: " + e.getMessage(), false);
            e.printStackTrace();
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
                    pieChartData.add(new PieChart.Data("No Adoptados (" + notAdopted + ")", notAdopted));
                }
            } else {
                pieChartData.add(new PieChart.Data("Sin datos", 1));
            }

            adoptionPieChart.setData(pieChartData);

        } catch (Exception e) {
            updateStatus("Error al actualizar gráfico circular: " + e.getMessage(), false);
            e.printStackTrace();
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
            statusLabel.setStyle(success ?
                    "-fx-text-fill: #27ae60;" :
                    "-fx-text-fill: #e74c3c;");
        }
    }

    private void updateLastUpdateTime() {
        if (lastUpdateLabel != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            lastUpdateLabel.setText("Última actualización: " + LocalDateTime.now().format(formatter));
        }
    }
}
