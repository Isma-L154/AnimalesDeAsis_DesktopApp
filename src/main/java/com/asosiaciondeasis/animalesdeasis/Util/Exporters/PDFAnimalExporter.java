package com.asosiaciondeasis.animalesdeasis.Util.Exporters;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.borders.SolidBorder;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for exporting animal medical records to PDF format.
 * Generates comprehensive reports including animal details and vaccination history.
 * Includes file selection dialog functionality.
 */
public class PDFAnimalExporter {

    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(52, 73, 94);
    private static final DeviceRgb ACCENT_COLOR = new DeviceRgb(52, 152, 219);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(236, 240, 241);

    /**
     * Exports an animal's complete medical record to PDF format with file selection dialog.
     * This method handles thread safety by ensuring UI operations run on JavaFX Application Thread.
     *
     * @param animal The animal whose record will be exported
     * @param place The place where the animal was rescued
     * @param vaccines List of vaccines administered to the animal
     * @param parentStage Parent stage for the file chooser dialog
     * @return CompletableFuture that completes with the file path where the PDF was saved, or null if user cancelled
     */
    public CompletableFuture<String> exportAnimalRecordWithDialog(Animal animal, Place place, List<Vaccine> vaccines, Stage parentStage) {
        CompletableFuture<String> future = new CompletableFuture<>();

        // Ensure FileChooser runs on JavaFX Application Thread
        if (Platform.isFxApplicationThread()) {
            // Already on FX thread, execute directly
            handleFileSelection(animal, place, vaccines, parentStage, future);
        } else {
            // Not on FX thread, switch to it
            Platform.runLater(() -> handleFileSelection(animal, place, vaccines, parentStage, future));
        }

        return future;
    }

    /**
     * Handles the file selection dialog and PDF generation
     */
    private void handleFileSelection(Animal animal, Place place, List<Vaccine> vaccines, Stage parentStage, CompletableFuture<String> future) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Guardar Expediente del Animal");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf")
            );

            // Create default filename
            String animalName = animal.getName() != null && !animal.getName().trim().isEmpty()
                    ? animal.getName().replaceAll("[^a-zA-Z0-9]", "_")
                    : "Animal";
            String defaultFileName = "Expediente_" + animalName + "_" + animal.getRecordNumber() + ".pdf";
            fileChooser.setInitialFileName(defaultFileName);

            // Show save dialog
            File file = fileChooser.showSaveDialog(parentStage);

            if (file != null) {
                // Generate PDF in background thread
                CompletableFuture.runAsync(() -> {
                    try {
                        exportAnimalRecord(animal, place, vaccines, file.getAbsolutePath());
                        future.complete(file.getAbsolutePath());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
            } else {
                future.complete(null); // User cancelled
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * Exports an animal's complete medical record to PDF format.
     *
     * @param animal The animal whose record will be exported
     * @param place The place where the animal was rescued
     * @param vaccines List of vaccines administered to the animal
     * @param filePath Destination path for the PDF file
     * @throws FileNotFoundException if the file path is invalid
     */
    public void exportAnimalRecord(Animal animal, Place place, List<Vaccine> vaccines, String filePath)
            throws FileNotFoundException {

        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        try {
            addHeader(document, animal);
            addAnimalDetails(document, animal, place);
            addVaccineHistory(document, vaccines);
            addFooter(document);
        } finally {
            document.close();
        }
    }

    /**
     * Adds the document header with title and animal identification.
     */
    private void addHeader(Document document, Animal animal) {
        // Title
        Paragraph title = new Paragraph("EXPEDIENTE")
                .setFontSize(20)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10);
        document.add(title);

        // Animal ID
        Paragraph animalId = new Paragraph("Registro N°: " + animal.getRecordNumber())
                .setFontSize(14)
                .setBold()
                .setFontColor(ACCENT_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(animalId);

        // Generation date
        Paragraph generationDate = new Paragraph("Generado el: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginBottom(30);
        document.add(generationDate);
    }

    /**
     * Adds animal basic information section.
     */
    private void addAnimalDetails(Document document, Animal animal, Place place) {
        // Section title
        Paragraph sectionTitle = new Paragraph("INFORMACIÓN DEL ANIMAL")
                .setFontSize(16)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setMarginBottom(15);
        document.add(sectionTitle);

        // Create table for animal details
        Table table = new Table(2);
        table.setWidth(UnitValue.createPercentValue(100));

        addDetailRow(table, "Nombre:", validateField(animal.getName()));
        addDetailRow(table, "Especie:", validateField(animal.getSpecies()));
        addDetailRow(table, "Sexo:", validateField(animal.getSex()));

        String ageText = animal.getApproximateAge() == 1 ?
                animal.getApproximateAge() + " año" :
                animal.getApproximateAge() + " años";
        addDetailRow(table, "Edad Aproximada:", ageText);

        addDetailRow(table, "Fecha de Ingreso:",
                formatDate(animal.getAdmissionDate()));
        addDetailRow(table, "Fecha de Castración:",
                formatDate(animal.getNeuteringDate()));
        addDetailRow(table, "Número de Chip:", validateField(animal.getChipNumber()));
        addDetailRow(table, "Recogido por:", validateField(animal.getCollectedBy()));

        if (place != null) {
            addDetailRow(table, "Lugar de Rescate:",
                    place.getName() + ", " + place.getProvinceName());
        } else {
            addDetailRow(table, "Lugar de Rescate:", "Sin información");
        }

        document.add(table);

        // Add multi-line fields
        if (animal.getReasonForRescue() != null && !animal.getReasonForRescue().isEmpty()) {
            addMultilineField(document, "Razón de Rescate:", animal.getReasonForRescue());
        }

        if (animal.getAilments() != null && !animal.getAilments().isEmpty()) {
            addMultilineField(document, "Afecciones Médicas:", animal.getAilments());
        }

        document.add(new Paragraph().setMarginBottom(20));
    }

    /**
     * Adds vaccination history section.
     */
    private void addVaccineHistory(Document document, List<Vaccine> vaccines) {
        Paragraph sectionTitle = new Paragraph("HISTORIAL DE VACUNACIÓN")
                .setFontSize(16)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setMarginBottom(15);
        document.add(sectionTitle);

        if (vaccines == null || vaccines.isEmpty()) {
            Paragraph noVaccines = new Paragraph("No hay registros de vacunación disponibles.")
                    .setFontColor(ColorConstants.GRAY)
                    .setItalic()
                    .setMarginBottom(20);
            document.add(noVaccines);
            return;
        }

        // Create vaccine table
        Table vaccineTable = new Table(4);
        vaccineTable.setWidth(UnitValue.createPercentValue(100));

        // Headers
        vaccineTable.addHeaderCell(createHeaderCell("Vacuna"));
        vaccineTable.addHeaderCell(createHeaderCell("Fecha"));

        // Data rows
        for (Vaccine vaccine : vaccines) {
            vaccineTable.addCell(createDataCell(validateField(vaccine.getVaccineName())));
            vaccineTable.addCell(createDataCell(formatDate(vaccine.getVaccinationDate())));
        }

        document.add(vaccineTable);
    }

    /**
     * Adds document footer with generation info.
     */
    private void addFooter(Document document) {
        document.add(new Paragraph().setMarginTop(30));

        Paragraph footer = new Paragraph("Documento generado automáticamente por el Sistema de Gestión de Animales")
                .setFontSize(8)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(footer);
    }

    // Helper methods
    private void addDetailRow(Table table, String label, String value) {
        table.addCell(createLabelCell(label));
        table.addCell(createValueCell(value));
    }

    private void addMultilineField(Document document, String label, String value) {
        Paragraph fieldLabel = new Paragraph(label)
                .setBold()
                .setFontColor(HEADER_COLOR)
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(fieldLabel);

        Paragraph fieldValue = new Paragraph(validateField(value))
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(8)
                .setMarginBottom(10);
        document.add(fieldValue);
    }

    private Cell createHeaderCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(ACCENT_COLOR)
                .setPadding(8)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell createDataCell(String text) {
        return new Cell()
                .add(new Paragraph(text))
                .setPadding(6)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 1));
    }

    private Cell createLabelCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold())
                .setBackgroundColor(LIGHT_GRAY)
                .setPadding(8);
    }

    private Cell createValueCell(String text) {
        return new Cell()
                .add(new Paragraph(text))
                .setPadding(8);
    }

    private String validateField(String value) {
        return (value == null || value.trim().isEmpty()) ? "Sin información" : value;
    }

    private String formatDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return "Sin información";
        }
        try {
            return DateUtils.utcStringToLocalDate(dateString)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return "Fecha inválida";
        }
    }
}