package com.asosiaciondeasis.animalesdeasis.Util.Exporters;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsDAO;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/**
 * Exports a year's statistics to a CSV file that opens in Excel, LibreOffice,
 * Google Sheets or any text editor.
 *
 * <p>Split in two because the halves belong on different threads: the file
 * chooser must run on the JavaFX application thread, while the queries and the
 * write must not.</p>
 */
public class CsvStatisticsExporter {

    private static final Locale SPANISH = Locale.of("es", "ES");

    private final IStatisticsDAO statisticsDAO;

    public CsvStatisticsExporter(IStatisticsDAO statisticsDAO) {
        this.statisticsDAO = statisticsDAO;
    }

    /**
     * Asks where to save. JavaFX application thread only.
     *
     * @return the chosen file, with a {@code .csv} extension, or {@code null} if cancelled
     */
    public File chooseFile(int year, Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar estadísticas como CSV");
        fileChooser.setInitialFileName("Estadisticas_" + year + ".csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos CSV (*.csv)", "*.csv"));

        File selectedFile = fileChooser.showSaveDialog(ownerWindow);
        if (selectedFile == null || selectedFile.getName().toLowerCase().endsWith(".csv")) {
            return selectedFile;
        }
        return new File(selectedFile.getAbsolutePath() + ".csv");
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
     * <p>Queries the database and writes the file: call it off the interface thread.</p>
     *
     * @param file The target file where CSV content will be written
     * @param year The year for which to generate statistics
     * @throws Exception if the statistics cannot be read or the file cannot be written
     */
    public void exportToFile(File file, int year) throws Exception {
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
            throw new IOException("Could not write CSV file " + file + ": " + e.getMessage(), e);
        }
    }

    /** Full Spanish month name for {@code "01"}..{@code "12"}, or a placeholder for anything else. */
    private String monthNumberToName(String monthNumber) {
        try {
            int month = Integer.parseInt(monthNumber);
            if (month >= 1 && month <= 12) {
                return Month.of(month).getDisplayName(TextStyle.FULL, SPANISH);
            }
        } catch (NumberFormatException e) {
            // Falls through to the placeholder; the row is still worth writing.
        }
        return "Mes desconocido";
    }
}
