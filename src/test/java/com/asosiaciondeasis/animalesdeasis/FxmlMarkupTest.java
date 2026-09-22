package com.asosiaciondeasis.animalesdeasis;

import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every view's markup has to load.
 *
 * <p>Five views declared their stylesheets as {@code <String fx:value=...>}
 * without importing {@code java.lang.String}, which {@code FXMLLoader} does not
 * import implicitly. Opening an animal's record, editing it and every vaccine
 * screen failed with "String is not a valid type", and CI stayed green because
 * nothing loaded those files.</p>
 *
 * <p>Each file is loaded without its controller - the controller attribute and
 * {@code #handler} references are stripped - so this checks the markup alone:
 * element types, imports and properties, with no application code running.</p>
 */
class FxmlMarkupTest {

    private static final Path FXML_ROOT = Path.of("src", "main", "resources", "fxml");

    @BeforeAll
    static void startToolkit() throws Exception {
        JavaFxToolkit.start();
    }

    static Stream<Path> views() throws IOException {
        return Files.walk(FXML_ROOT).filter(path -> path.toString().endsWith(".fxml"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("views")
    void markupLoads(Path view) throws Exception {
        String markup = Files.readString(view, StandardCharsets.UTF_8)
                .replaceAll("\\s+fx:controller=\"[^\"]*\"", "")
                .replaceAll("\\s+on[A-Z]\\w*=\"#[^\"]*\"", "");

        JavaFxToolkit.onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(view.toUri().toURL());
            assertNotNull(loader.load(new ByteArrayInputStream(markup.getBytes(StandardCharsets.UTF_8))));
        });
    }
}
