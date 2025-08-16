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
 */
public class CsvStatisticsExporter {

    private final StatisticsDAO statisticsDAO;

    public CsvStatisticsExporter(StatisticsDAO statisticsDAO) {
        this.statisticsDAO = statisticsDAO;
    }

    /**
     * Exports statistics data to a CSV file for the specified year with a file chooser dialog.
     * @return true if export was successful, false if cancelled by user
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
     * Method overload to use in non-GUI contexts, exports using a default file path
     */
    public void export(int year) throws Exception {
        export(year, null);
    }

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
     * Checks if there is data to export for the given year
     */
    public boolean hasDataToExport(int year) {
        try {
            return statisticsDAO.getTotalAdmissions(year) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
