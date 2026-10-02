package com.asosiaciondeasis.animalesdeasis.Util.Exporters;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Statistics.IStatisticsDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the exported file back, since the file is what the association hands to other people. */
class CsvStatisticsExporterTest {

    private static final String RULE = "=".repeat(60);

    @TempDir
    Path tempDir;

    private record FixedStatistics(Map<String, Integer> monthly, Map<String, Integer> origins,
                                   int total, double rate) implements IStatisticsDAO {
        @Override
        public Map<String, Integer> getMonthlyAdmissions(int year) {
            return monthly;
        }

        @Override
        public Map<String, Integer> getAnimalOrigins(int year) {
            return origins;
        }

        @Override
        public int getTotalAdmissions(int year) {
            return total;
        }

        @Override
        public double getAdoptionRate(int year) {
            return rate;
        }
    }

    /** The file's lines, with the three that carry the time of the export masked. */
    private List<String> export(IStatisticsDAO statistics) throws Exception {
        Path target = tempDir.resolve("estadisticas.csv");
        new CsvStatisticsExporter(statistics).exportToFile(target.toFile(), 2024);
        return Files.readAllLines(target, StandardCharsets.UTF_8).stream()
                .map(line -> line.replaceFirst("^(Generado el: |Fecha de Generación,|Hora de Generación,).*", "$1<now>"))
                .toList();
    }

    @Test
    void writesEverySectionOfAYearWithRecords() throws Exception {
        Map<String, Integer> monthly = new LinkedHashMap<>();
        monthly.put("01", 3);
        monthly.put("02", 5);
        Map<String, Integer> origins = new LinkedHashMap<>();
        origins.put("Escazú, San José", 5);
        origins.put("Liberia, Guanacaste", 3);

        List<String> lines = export(new FixedStatistics(monthly, origins, 8, 25.0));

        assertEquals(List.of(
                "# ARCHIVO CSV - Compatible con Excel, LibreOffice, Google Sheets y cualquier editor de texto",
                "# Para abrir: Haga doble clic o abra con Excel, Notepad, Word, etc.",
                "#",
                "Asociación de Asís - Reporte de Estadísticas del Año 2024",
                "Generado el: <now>",
                RULE,
                "",
                "RESUMEN EJECUTIVO",
                "Indicador,Valor",
                "Año,2024",
                "Total de Admisiones,8",
                "Tasa de Adopción (%),25.00",
                "Promedio Mensual,4.00",
                "",
                RULE,
                "",
                "ADMISIONES MENSUALES DETALLADAS",
                "Mes,Número,Nombre del Mes",
                "01,3,enero",
                "02,5,febrero",
                "",
                RULE,
                "",
                "ANÁLISIS DE ADOPCIONES",
                "Concepto,Cantidad,Porcentaje",
                "Animales Adoptados,2,25.00",
                "Animales No Adoptados,6,75.00",
                "",
                RULE,
                "",
                "ORIGEN DE ANIMALES POR LUGAR",
                "Lugar - Provincia,Cantidad",
                // The comma inside a place name would otherwise split it into two columns.
                "Escazú - San José,5",
                "Liberia - Guanacaste,3",
                "",
                RULE,
                "",
                "METADATOS DEL REPORTE",
                "Campo,Valor",
                "Sistema,Dashboard de Estadísticas",
                "Versión,1.0",
                "Fecha de Generación,<now>",
                "Hora de Generación,<now>",
                "Total de Registros Procesados,8"), lines);
    }

    @Test
    void aYearWithNoRecordsSaysSoInsteadOfLeavingSectionsBlank() throws Exception {
        List<String> lines = export(new FixedStatistics(Map.of(), Map.of(), 0, 0.0));

        assertTrue(lines.contains("Total de Admisiones,0"), lines.toString());
        assertTrue(lines.contains("Sin datos disponibles,0,0.00"), lines.toString());
        assertTrue(lines.contains("Sin datos disponibles,0"), lines.toString());
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Promedio Mensual")),
                "an average over no months is not a figure");
    }

    @Test
    void listsAtMostFifteenOrigins() throws Exception {
        Map<String, Integer> origins = new LinkedHashMap<>();
        for (int i = 1; i <= 20; i++) {
            origins.put("Lugar " + i + ", Provincia", 21 - i);
        }

        List<String> lines = export(new FixedStatistics(Map.of("01", 20), origins, 20, 0.0));

        assertEquals(15, lines.stream().filter(line -> line.startsWith("Lugar ") && !line.startsWith("Lugar -")).count());
        assertTrue(lines.contains("Lugar 15 - Provincia,6"), lines.toString());
        assertFalse(lines.contains("Lugar 16 - Provincia,5"), lines.toString());
    }

    /** PrintWriter swallows write failures, so a full disk used to be reported as a finished export. */
    @Test
    void aFailedWriteIsReportedInsteadOfLookingLikeSuccess() {
        PrintWriter failing = new PrintWriter(new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        CsvStatisticsExporter.YearReport report =
                new CsvStatisticsExporter.YearReport(2024, Map.of("01", 1), 1, 0.0, Map.of());

        assertThrows(IOException.class,
                () -> CsvStatisticsExporter.write(failing, report, LocalDateTime.now()));
    }
}
