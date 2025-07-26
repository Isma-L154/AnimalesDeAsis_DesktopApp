package com.asosiaciondeasis.animalesdeasis.Util.Exporters;


import com.asosiaciondeasis.animalesdeasis.DAO.Statistics.StatisticsDAO;

import javax.swing.*;
import java.io.*;
import java.time.Month;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

//TODO FINISH THIS CLASS

public class CsvStatisticsExporter {

    private final StatisticsDAO statisticsDAO;


    public CsvStatisticsExporter(StatisticsDAO statisticsDAO) {
        this.statisticsDAO = statisticsDAO;
    }

    public void export(int year) throws Exception{

        /** The user can choose where to save the csv file */
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar estadísticas como CSV");
        fileChooser.setSelectedFile(new File("Estadisticas_" + year + ".csv"));

        int userSelection = fileChooser.showSaveDialog(null);

        if (userSelection != JFileChooser.APPROVE_OPTION) {
            System.out.println("Exportación cancelada por el usuario.");
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(selectedFile), "UTF-8"))) {
            writer.println("Asociación de Asís - Reporte de Estadísticas del Año " + year);
            writer.println("Generado el: " + new Date());
            writer.println("=".repeat(50));
            writer.println();

            // Sección 1: Admisiones por mes
            writer.println("📈 Admisiones mensuales");
            writer.println("Mes,Total de Admisiones");

            Map<String, Integer> monthlyAdmissions = statisticsDAO.getMonthlyAdmissions(year);
            for (Map.Entry<String, Integer> entry : monthlyAdmissions.entrySet()) {
                String monthName = monthNumberToName(entry.getKey());
                writer.println(monthName + "," + entry.getValue());
            }

            writer.println();
            writer.println("=".repeat(50));
            writer.println();

            // Sección 2: Total de ingresos anuales
            int total = statisticsDAO.getTotalAdmissions(year);
            writer.println("📊 Total de ingresos en el año " + year);
            writer.println("Total," + total);

            writer.println();
            writer.println("=".repeat(50));
            writer.println();

            // Sección 3: Tasa de adopción
            double adoptionRate = statisticsDAO.getAdoptionRate(year);
            writer.println("🎯 Tasa de adopción");
            writer.println("Año,Tasa de Adopción (%)");
            writer.println(year + "," + String.format(Locale.US, "%.2f", adoptionRate));
        } catch (IOException e) {
            throw new Exception("Error exporting data");
        }
    }

    private String monthNumberToName(String monthNumber) {
        try {
            int month = Integer.parseInt(monthNumber);
            return Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, new Locale("es"));
        } catch (Exception e) {
            return "Mes desconocido";
        }
    }
}
