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
    private static final String RULE = "=".repeat(60);
    private static final int MAX_ORIGINS = 15;

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

    /** Everything the report shows, read before a single line is written. */
    record YearReport(int year, Map<String, Integer> monthlyAdmissions, int totalAdmissions,
                      double adoptionRate, Map<String, Integer> origins) {
    }

    /**
     * Queries the database and writes the file: call it off the interface thread.
     *
     * <p>The figures are read first, so a failed query leaves no half-written
     * report behind.</p>
     *
     * @throws Exception if the statistics cannot be read or the file cannot be written
     */
    public void exportToFile(File file, int year) throws Exception {
        YearReport report = new YearReport(year,
                statisticsDAO.getMonthlyAdmissions(year),
                statisticsDAO.getTotalAdmissions(year),
                statisticsDAO.getAdoptionRate(year),
                statisticsDAO.getAnimalOrigins(year));

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            write(writer, report, LocalDateTime.now());
        } catch (IOException e) {
            throw new IOException("Could not write CSV file " + file + ": " + e.getMessage(), e);
        }
    }

    /** Package-visible so a test can hand it a writer that fails. */
    static void write(PrintWriter writer, YearReport report, LocalDateTime generatedAt) throws IOException {
        writeHeader(writer, report.year(), generatedAt);
        writeSummary(writer, report);
        writeSeparator(writer);
        writeMonthlyAdmissions(writer, report.monthlyAdmissions());
        writeSeparator(writer);
        writeAdoptions(writer, report.totalAdmissions(), report.adoptionRate());
        writeSeparator(writer);
        writeOrigins(writer, report.origins());
        writeSeparator(writer);
        writeMetadata(writer, report.totalAdmissions(), generatedAt);

        // PrintWriter never throws: it records the failure and carries on. Without
        // this, a disk that fills up halfway is reported as a completed export.
        if (writer.checkError()) {
            throw new IOException("the report could not be written completely");
        }
    }

    private static void writeHeader(PrintWriter writer, int year, LocalDateTime generatedAt) {
        writer.println("# ARCHIVO CSV - Compatible con Excel, LibreOffice, Google Sheets y cualquier editor de texto");
        writer.println("# Para abrir: Haga doble clic o abra con Excel, Notepad, Word, etc.");
        writer.println("#");
        writer.println("Asociación de Asís - Reporte de Estadísticas del Año " + year);
        writer.println("Generado el: " + generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        writer.println(RULE);
        writer.println();
    }

    private static void writeSeparator(PrintWriter writer) {
        writer.println();
        writer.println(RULE);
        writer.println();
    }

    private static void writeSummary(PrintWriter writer, YearReport report) {
        writer.println("RESUMEN EJECUTIVO");
        writer.println("Indicador,Valor");
        writer.println("Año," + report.year());
        writer.println("Total de Admisiones," + report.totalAdmissions());
        writer.println("Tasa de Adopción (%)," + decimal(report.adoptionRate()));

        if (!report.monthlyAdmissions().isEmpty()) {
            double monthlyAverage = report.monthlyAdmissions().values().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);
            writer.println("Promedio Mensual," + decimal(monthlyAverage));
        }
    }

    private static void writeMonthlyAdmissions(PrintWriter writer, Map<String, Integer> monthlyAdmissions) {
        writer.println("ADMISIONES MENSUALES DETALLADAS");
        writer.println("Mes,Número,Nombre del Mes");
        monthlyAdmissions.forEach((monthNumber, count) ->
                writer.println(monthNumber + "," + count + "," + monthNumberToName(monthNumber)));
    }

    private static void writeAdoptions(PrintWriter writer, int totalAdmissions, double adoptionRate) {
        writer.println("ANÁLISIS DE ADOPCIONES");
        writer.println("Concepto,Cantidad,Porcentaje");

        if (totalAdmissions == 0) {
            writer.println("Sin datos disponibles,0,0.00");
            return;
        }
        int adopted = (int) Math.round(totalAdmissions * adoptionRate / 100.0);
        writer.println("Animales Adoptados," + adopted + "," + decimal(adoptionRate));
        writer.println("Animales No Adoptados," + (totalAdmissions - adopted) + "," + decimal(100.0 - adoptionRate));
    }

    private static void writeOrigins(PrintWriter writer, Map<String, Integer> origins) {
        writer.println("ORIGEN DE ANIMALES POR LUGAR");
        writer.println("Lugar - Provincia,Cantidad");

        if (origins.isEmpty()) {
            writer.println("Sin datos disponibles,0");
            return;
        }
        // An origin is "place, province"; its comma would split it into two columns.
        origins.entrySet().stream()
                .limit(MAX_ORIGINS)
                .forEach(entry -> writer.println(entry.getKey().replace(",", " -") + "," + entry.getValue()));
    }

    private static void writeMetadata(PrintWriter writer, int totalAdmissions, LocalDateTime generatedAt) {
        writer.println("METADATOS DEL REPORTE");
        writer.println("Campo,Valor");
        writer.println("Sistema,Dashboard de Estadísticas");
        writer.println("Versión,1.0");
        writer.println("Fecha de Generación," + generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        writer.println("Hora de Generación," + generatedAt.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        writer.println("Total de Registros Procesados," + totalAdmissions);
    }

    /** A dot as the decimal separator whatever the machine's locale: a comma would add a column. */
    private static String decimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** Full Spanish month name for {@code "01"}..{@code "12"}, or a placeholder for anything else. */
    private static String monthNumberToName(String monthNumber) {
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
