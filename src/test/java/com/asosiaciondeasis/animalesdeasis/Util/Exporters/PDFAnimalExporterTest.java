package com.asosiaciondeasis.animalesdeasis.Util.Exporters;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Place;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end checks for the PDF export.
 *
 * <p>These read the text back out of the generated file rather than mocking the PDF library, because the
 * failure modes worth guarding against here are the ones a mock would hide: an API that moved
 * between library versions, a missing transitive artifact, or an encoding that silently drops the
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
        // Read from bytes: a PdfReader opened on the path memory-maps the file,
        // which Windows keeps locked until the mapping is collected.
        try (PdfReader reader = new PdfReader(Files.readAllBytes(target))) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page));
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
                new Place(1, "Escazú", "San José"),
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
        // the library packs two vaccines into one physical row and they end up on the same line.
        // Runs of whitespace are collapsed, and only "no two vaccines share a line" is asserted:
        // where the extractor places the text that follows the table is not part of the invariant.
        String collapsed = text.replaceAll("[ \\t]+", " ");
        assertTrue(collapsed.contains("Rabia 10/03/2026"), text);
        assertTrue(collapsed.contains("Moquillo 05/04/2026"), text);
        assertTrue(collapsed.lines().noneMatch(line -> line.contains("Rabia") && line.contains("Moquillo")),
                text);
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
