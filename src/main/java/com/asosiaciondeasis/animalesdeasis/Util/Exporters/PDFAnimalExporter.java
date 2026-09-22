package com.asosiaciondeasis.animalesdeasis.Util.Exporters;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.DateUtils;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Exports an animal's record - details and vaccination history - to PDF.
 *
 * <p>Built on OpenPDF (LGPL/MPL) rather than iText, whose AGPL licence does not
 * fit an MIT project that publishes installers. Helvetica is one of the PDF
 * base-14 fonts, so nothing gets embedded and the file stays small, and its
 * WinAnsi encoding covers the Spanish accents and the "N°" sign the report uses.</p>
 */
public class PDFAnimalExporter {

    private static final Color HEADER_COLOR = new Color(52, 73, 94);
    private static final Color ACCENT_COLOR = new Color(52, 152, 219);
    private static final Color LIGHT_GRAY = new Color(236, 240, 241);
    private static final Color BORDER_GRAY = new Color(211, 211, 211);

    private static final String NO_INFO = "Sin información";

    /**
     * Asks where to save, then writes the PDF in the background. The file chooser
     * runs on the JavaFX application thread whichever thread this is called from.
     *
     * @return completes with the saved file's path, or {@code null} if the user cancelled
     */
    public CompletableFuture<String> exportAnimalRecordWithDialog(Animal animal, Place place, List<Vaccine> vaccines, Stage parentStage) {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (Platform.isFxApplicationThread()) {
            handleFileSelection(animal, place, vaccines, parentStage, future);
        } else {
            Platform.runLater(() -> handleFileSelection(animal, place, vaccines, parentStage, future));
        }
        return future;
    }

    private void handleFileSelection(Animal animal, Place place, List<Vaccine> vaccines, Stage parentStage, CompletableFuture<String> future) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Guardar Expediente del Animal");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf"));

            String animalName = animal.getName() != null && !animal.getName().isBlank()
                    ? animal.getName().replaceAll("[^a-zA-Z0-9]", "_")
                    : "Animal";
            fileChooser.setInitialFileName("Expediente_" + animalName + "_" + animal.getRecordNumber() + ".pdf");

            File file = fileChooser.showSaveDialog(parentStage);
            if (file == null) {
                future.complete(null);
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    exportAnimalRecord(animal, place, vaccines, file.getAbsolutePath());
                    future.complete(file.getAbsolutePath());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * Writes the record to {@code filePath}.
     *
     * @throws IOException if the file cannot be written or the document cannot be built
     */
    public void exportAnimalRecord(Animal animal, Place place, List<Vaccine> vaccines, String filePath)
            throws IOException {
        // The stream is closed on every path, so a failure while building the
        // document never leaves a half-written file locked on Windows.
        try (OutputStream out = new FileOutputStream(filePath)) {
            Document document = new Document();
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer());
            document.open();
            try {
                addHeader(document, animal);
                addAnimalDetails(document, animal, place);
                addVaccineHistory(document, vaccines);
            } finally {
                document.close();
            }
        } catch (DocumentException e) {
            throw new IOException("Could not build the PDF for " + animal.getRecordNumber(), e);
        }
    }

    private void addHeader(Document document, Animal animal) {
        Paragraph title = new Paragraph("EXPEDIENTE", font(FontFactory.HELVETICA_BOLD, 20, HEADER_COLOR));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        Paragraph animalId = new Paragraph("Registro N°: " + animal.getRecordNumber(),
                font(FontFactory.HELVETICA_BOLD, 14, ACCENT_COLOR));
        animalId.setAlignment(Element.ALIGN_CENTER);
        animalId.setSpacingAfter(20);
        document.add(animalId);

        Paragraph generated = new Paragraph("Generado el: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                font(FontFactory.HELVETICA, 10, Color.BLACK));
        generated.setAlignment(Element.ALIGN_RIGHT);
        generated.setSpacingAfter(30);
        document.add(generated);
    }

    private void addAnimalDetails(Document document, Animal animal, Place place) {
        document.add(sectionTitle("INFORMACIÓN DEL ANIMAL"));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        addDetailRow(table, "Nombre:", orNoInfo(animal.getName()));
        addDetailRow(table, "Especie:", orNoInfo(animal.getSpecies()));
        addDetailRow(table, "Sexo:", orNoInfo(animal.getSex()));
        int age = animal.getApproximateAge();
        addDetailRow(table, "Edad Aproximada:", age + (age == 1 ? " año" : " años"));
        addDetailRow(table, "Fecha de Ingreso:", formatDate(animal.getAdmissionDate()));
        addDetailRow(table, "Fecha de Castración:", formatDate(animal.getNeuteringDate()));
        addDetailRow(table, "Número de Chip:", orNoInfo(animal.getChipNumber()));
        addDetailRow(table, "Recogido por:", orNoInfo(animal.getCollectedBy()));
        addDetailRow(table, "Lugar de Rescate:",
                place != null ? place.name() + ", " + place.provinceName() : NO_INFO);
        document.add(table);

        if (animal.getReasonForRescue() != null && !animal.getReasonForRescue().isEmpty()) {
            addMultilineField(document, "Razón de Rescate:", animal.getReasonForRescue());
        }
        if (animal.getAilments() != null && !animal.getAilments().isEmpty()) {
            addMultilineField(document, "Afecciones Médicas:", animal.getAilments());
        }

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(20);
        document.add(spacer);
    }

    private void addVaccineHistory(Document document, List<Vaccine> vaccines) {
        document.add(sectionTitle("HISTORIAL DE VACUNACIÓN"));

        if (vaccines == null || vaccines.isEmpty()) {
            Paragraph none = new Paragraph("No hay registros de vacunación disponibles.",
                    font(FontFactory.HELVETICA_OBLIQUE, 12, Color.GRAY));
            none.setSpacingAfter(20);
            document.add(none);
            return;
        }

        // Two columns, matching the two cells written per vaccine below. Declaring
        // more would pack several vaccines into a single physical row.
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.addCell(headerCell("Vacuna"));
        table.addCell(headerCell("Fecha"));
        for (Vaccine vaccine : vaccines) {
            table.addCell(dataCell(orNoInfo(vaccine.getVaccineName())));
            table.addCell(dataCell(formatDate(vaccine.getVaccinationDate())));
        }
        document.add(table);
    }

    /**
     * Drawn in the bottom margin of every page. As a paragraph at the end of the
     * flow it spilled onto a page of its own whenever the record nearly filled one.
     */
    private static final class Footer extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase("Documento generado automáticamente por el Sistema de Gestión de Animales",
                            font(FontFactory.HELVETICA, 8, Color.GRAY)),
                    (document.left() + document.right()) / 2, document.bottom() - 18, 0);
        }
    }

    private static Paragraph sectionTitle(String text) {
        Paragraph title = new Paragraph(text, font(FontFactory.HELVETICA_BOLD, 16, HEADER_COLOR));
        title.setSpacingAfter(15);
        return title;
    }

    private static void addDetailRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font(FontFactory.HELVETICA_BOLD, 12, Color.BLACK)));
        labelCell.setBackgroundColor(LIGHT_GRAY);
        labelCell.setPadding(8);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font(FontFactory.HELVETICA, 12, Color.BLACK)));
        valueCell.setPadding(8);
        table.addCell(valueCell);
    }

    private static void addMultilineField(Document document, String label, String value) {
        Paragraph fieldLabel = new Paragraph(label, font(FontFactory.HELVETICA_BOLD, 12, HEADER_COLOR));
        fieldLabel.setSpacingBefore(10);
        fieldLabel.setSpacingAfter(5);
        document.add(fieldLabel);

        // A one-cell table rather than a paragraph, because only a cell can carry
        // the shaded background and padding the section is drawn with.
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(orNoInfo(value), font(FontFactory.HELVETICA, 12, Color.BLACK)));
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(8);
        cell.setBorder(Rectangle.NO_BORDER);
        box.addCell(cell);
        box.setSpacingAfter(10);
        document.add(box);
    }

    private static PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(FontFactory.HELVETICA_BOLD, 12, Color.WHITE)));
        cell.setBackgroundColor(ACCENT_COLOR);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private static PdfPCell dataCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(FontFactory.HELVETICA, 12, Color.BLACK)));
        cell.setPadding(6);
        cell.setBorderColor(BORDER_GRAY);
        return cell;
    }

    private static Font font(String name, float size, Color color) {
        return FontFactory.getFont(name, size, Font.NORMAL, color);
    }

    private static String orNoInfo(String value) {
        return (value == null || value.isBlank()) ? NO_INFO : value;
    }

    private static String formatDate(String stored) {
        if (stored == null || stored.isEmpty()) {
            return NO_INFO;
        }
        try {
            return DateUtils.utcStringToLocalDate(stored).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return "Fecha inválida";
        }
    }
}
