package com.asosiaciondeasis.animalesdeasis.Util.Exporters;


import com.asosiaciondeasis.animalesdeasis.DAO.Statistics.StatisticsDAO;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * CsvStatisticsExporter is responsible for exporting statistics data to a CSV file.
 * It provides methods to export data for a specific year, either through a GUI file chooser
 * or directly to a specified file path.
 *
 * The exported CSV file is compatible with Excel, LibreOffice, Google Sheets, and any text editor.
 * It includes comprehensive statistics including monthly admissions, adoption rates, and animal origins.
 */
public class CsvStatisticsExporter {

    private final StatisticsDAO statisticsDAO;

    /**
     * Constructor that initializes the exporter with a StatisticsDAO instance.
     *
     * @param statisticsDAO The data access object used to retrieve statistics from the database
     */
    public CsvStatisticsExporter(StatisticsDAO statisticsDAO) {
        this.statisticsDAO = statisticsDAO;
    }

    /**
     * Exports statistics data to a CSV file for the specified year using a file chooser dialog.
     *
     * This method:
     * 1. Opens a file chooser dialog for the user to select save location
     * 2. Sets default filename with year suffix
     * 3. Ensures .csv extension is added if not provided
     * 4. Calls exportToFile() to generate the actual CSV content
     *
     * @param year The year for which to export statistics
     * @param ownerWindow The parent window for the file chooser dialog
     * @return true if export was successful, false if canceled by user
     * @throws Exception if there's an error during the export process
     */
    public boolean export(int year, Window ownerWindow) throws Exception {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar estadísticas como CSV");
        fileChooser.setInitialFileName("Estadisticas_" + year + ".csv");

        FileChooser.ExtensionFilter csvFilter =
                new FileChooser.ExtensionFilter("Archivos CSV (*.csv)", "*.csv");
        fileChooser.getExtensionFilters().add(csvFilter);

        File selectedFile = fileChooser.showSaveDialog(ownerWindow);

        if (selectedFile == null) {
            System.out.println("Exportación cancelada por el usuario.");
            return false;
        }

        if (!selectedFile.getName().toLowerCase().endsWith(".csv")) {
            selectedFile = new File(selectedFile.getAbsolutePath() + ".csv");
        }

        exportToFile(selectedFile, year);
        return true;
    }

    /**
     * Method overload to use in non-GUI contexts, exports using a default file path.
     * This is a convenience method that calls the main export method with null parent window.
     *
     * @param year The year for which to export statistics
     * @throws Exception if there's an error during the export process
     */
    public void export(int year) throws Exception {
        export(year, null);
    }

    /**
     * Core method that generates and writes the CSV file content.
     *
     * The CSV structure includes:
     * 1. Header with compatibility information for users without Excel
     * 2. Executive summary with key metrics (total admissions, adoption rate, monthly average)
     * 3. Detailed monthly admissions breakdown with month names
     * 4. Adoption analysis showing adopted vs non-adopted animals
     * 5. Top 15 animal origins by location and province
     * 6. Metadata section with report generation details
     *
     * Uses UTF-8 encoding to ensure proper character display across different systems.
     * Handles comma escaping in location names to prevent CSV parsing issues.
     *
     * @param file The target file where CSV content will be written
     * @param year The year for which to generate statistics
     * @throws Exception if there's an I/O error during file writing
     */
    private void exportToFile(File file, int year) throws Exception {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {


            writer.println("# ARCHIVO CSV - Compatible con Excel, LibreOffice, Google Sheets y cualquier editor de texto");
            writer.println("# Para abrir: Haga doble clic o abra con Excel, Notepad, Word, etc.");
            writer.println("#");

            writer.println("Asociación de Asís - Reporte de Estadísticas del Año " + year);
            writer.println("Generado el: " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            writer.println("=".repeat(60));
            writer.println();


            Map<String, Integer> monthlyAdmissions = statisticsDAO.getMonthlyAdmissions(year);
            int totalAdmissions = statisticsDAO.getTotalAdmissions(year);
            double adoptionRate = statisticsDAO.getAdoptionRate(year);


            writer.println("RESUMEN EJECUTIVO");
            writer.println("Indicador,Valor");
            writer.println("Año," + year);
            writer.println("Total de Admisiones," + totalAdmissions);
            writer.println("Tasa de Adopción (%)," + String.format(Locale.US, "%.2f", adoptionRate));

            if (!monthlyAdmissions.isEmpty()) {
                double monthlyAverage = monthlyAdmissions.values().stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0);
                writer.println("Promedio Mensual," + String.format(Locale.US, "%.2f", monthlyAverage));
            }

            writer.println();
            writer.println("=".repeat(60));
            writer.println();


            writer.println("ADMISIONES MENSUALES DETALLADAS");
            writer.println("Mes,Número,Nombre del Mes");

            for (Map.Entry<String, Integer> entry : monthlyAdmissions.entrySet()) {
                String monthNumber = entry.getKey();
                String monthName = monthNumberToName(monthNumber);
                int count = entry.getValue();
                writer.println(monthNumber + "," + count + "," + monthName);
            }

            writer.println();
            writer.println("=".repeat(60));
            writer.println();


            writer.println("ANÁLISIS DE ADOPCIONES");
            writer.println("Concepto,Cantidad,Porcentaje");

            if (totalAdmissions > 0) {
                int adoptedAnimals = (int) Math.round(totalAdmissions * adoptionRate / 100.0);
                int notAdoptedAnimals = totalAdmissions - adoptedAnimals;

                writer.println("Animales Adoptados," + adoptedAnimals + "," +
                        String.format(Locale.US, "%.2f", adoptionRate));
                writer.println("Animales No Adoptados," + notAdoptedAnimals + "," +
                        String.format(Locale.US, "%.2f", 100.0 - adoptionRate));
            } else {
                writer.println("Sin datos disponibles,0,0.00");
            }

            writer.println();
            writer.println("=".repeat(60));
            writer.println();

            writer.println("ORIGEN DE ANIMALES POR LUGAR");
            writer.println("Lugar - Provincia,Cantidad");

            Map<String, Integer> originsData = statisticsDAO.getAnimalOrigins(year);

            if (!originsData.isEmpty()) {
                originsData.entrySet().stream()
                        .limit(15)
                        .forEach(entry -> {
                            String origin = entry.getKey().replace(",", " -");
                            int count = entry.getValue();
                            writer.println(origin + "," + count);
                        });
            } else {
                writer.println("Sin datos disponibles,0");
            }

            writer.println();
            writer.println("=".repeat(60));
            writer.println();

            writer.println("METADATOS DEL REPORTE");
            writer.println("Campo,Valor");
            writer.println("Sistema,Dashboard de Estadísticas");
            writer.println("Versión,1.0");
            writer.println("Fecha de Generación," + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            writer.println("Hora de Generación," + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm:ss")));
            writer.println("Total de Registros Procesados," + totalAdmissions);

        } catch (IOException e) {
            throw new Exception("Error al escribir el archivo CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a numeric month string to its full Spanish name.
     *
     * Uses Java's Month enum with Spanish locale to get proper month names.
     * Handles invalid month numbers gracefully by returning a default message.
     *
     * @param monthNumber String representation of month number (1-12)
     * @return Full Spanish month name or "Mes desconocido" if invalid
     */
    private String monthNumberToName(String monthNumber) {
        try {
            int month = Integer.parseInt(monthNumber);
            if (month >= 1 && month <= 12) {
                return Month.of(month).getDisplayName(
                        java.time.format.TextStyle.FULL,
                        new Locale("es", "ES"));
            }
        } catch (NumberFormatException e) {
            // Log error if needed
        }
        return "Mes desconocido";
    }

    /**
     * Validates if there is exportable data available for the specified year.
     *
     * This method is useful for UI components to determine whether to enable
     * export functionality or show appropriate messages to users.
     *
     * @param year The year to check for available data
     * @return true if there are admissions data for the year, false otherwise
     */
    public boolean hasDataToExport(int year) {
        try {
            return statisticsDAO.getTotalAdmissions(year) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
