package com.asosiaciondeasis.animalesdeasis.Util.Exporters;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end checks for the PDF export.
 *
 * <p>These read the text back out of the generated file rather than mocking iText, because the
 * failure modes worth guarding against here are the ones a mock would hide: an API that moved
 * between iText majors, a missing transitive artifact, or an encoding that silently drops the
 * Spanish accents from the report.
 */
class PDFAnimalExporterTest {

    @TempDir
    Path tempDir;

    private static Animal sampleAnimal() {
        Animal animal = new Animal();
        animal.setRecordNumber("A-2026-0042");
        animal.setName("Ñoño");
        animal.setSpecies("Canino");
        animal.setSex("Macho");
        animal.setApproximateAge(3);
        animal.setAdmissionDate("2026-01-15T00:00:00");
        animal.setNeuteringDate("2026-02-20T00:00:00");
        animal.setChipNumber("900123456789");
        animal.setCollectedBy("María Fernández");
        animal.setReasonForRescue("Abandonado en la vía pública");
        animal.setAilments("Desnutrición leve");
        return animal;
    }

    private String exportAndExtractText(Animal animal, Place place, List<Vaccine> vaccines) throws Exception {
        Path target = tempDir.resolve("expediente.pdf");
        new PDFAnimalExporter().exportAnimalRecord(animal, place, vaccines, target.toString());

        assertTrue(Files.size(target) > 0, "the exporter produced an empty file");

        StringBuilder text = new StringBuilder();
        try (PdfDocument pdf = new PdfDocument(new PdfReader(target.toString()))) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page)));
            }
        }
        return text.toString();
    }

    @Test
    void writesAReadableRecordWithAccentedText() throws Exception {
        Vaccine vaccine = new Vaccine();
        vaccine.setVaccineName("Rabia");
        vaccine.setVaccinationDate("2026-03-10T00:00:00");

        String text = exportAndExtractText(
                sampleAnimal(),
                new Place(1, "Escazú", "SJ", "San José"),
                List.of(vaccine));

        assertTrue(text.contains("EXPEDIENTE"), text);
        assertTrue(text.contains("A-2026-0042"), text);
        // Accents survive the base-14 Helvetica encoding.
        assertTrue(text.contains("INFORMACIÓN DEL ANIMAL"), text);
        assertTrue(text.contains("Ñoño"), text);
        assertTrue(text.contains("María Fernández"), text);
        assertTrue(text.contains("Desnutrición leve"), text);
        assertTrue(text.contains("San José"), text);
        // Dates are rendered in the display format, not the stored UTC string.
        assertTrue(text.contains("15/01/2026"), text);
        assertTrue(text.contains("Rabia"), text);
        assertTrue(text.contains("10/03/2026"), text);
    }

    @Test
    void listsEachVaccineOnItsOwnRow() throws Exception {
        Vaccine rabies = new Vaccine();
        rabies.setVaccineName("Rabia");
        rabies.setVaccinationDate("2026-03-10T00:00:00");

        Vaccine distemper = new Vaccine();
        distemper.setVaccineName("Moquillo");
        distemper.setVaccinationDate("2026-04-05T00:00:00");

        String text = exportAndExtractText(sampleAnimal(), null, List.of(rabies, distemper));

        // The table has to declare exactly the two columns that are filled per vaccine; with more,
        // iText packs two vaccines into one physical row and they end up on the same line.
        List<String> lines = text.lines().map(String::trim).toList();
        assertTrue(lines.contains("Rabia 10/03/2026"), text);
        assertTrue(lines.contains("Moquillo 05/04/2026"), text);
    }

    @Test
    void fallsBackToPlaceholdersWhenDataIsMissing() throws Exception {
        Animal sparse = new Animal();
        sparse.setRecordNumber("A-2026-0001");

        String text = exportAndExtractText(sparse, null, List.of());

        assertTrue(text.contains("No hay registros de vacunación disponibles."), text);
        assertTrue(text.contains("Sin información"), text);
    }
}
