package com.asosiaciondeasis.animalesdeasis;

import javafx.application.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shape of the entry point.
 *
 * <p>If the class holding {@code main} extends {@link Application}, the JavaFX
 * launcher checks that the JavaFX modules came from the module path and refuses
 * to start otherwise:</p>
 *
 * <pre>
 * Error: JavaFX runtime components are missing, and are required to run this
 * application
 * </pre>
 *
 * <p>An IDE runs a class by putting everything on the classpath, so pressing Run
 * failed while {@code mvn javafx:run} worked — the plugin builds a module path
 * and the IDE does not. Merging the two classes back together would bring that
 * straight back, and it would only show up for whoever next tries to run the
 * project from an editor rather than from Maven.</p>
 */
class LaunchEntryPointTest {

    @Test
    @DisplayName("the class holding main is not itself a JavaFX Application")
    void entryPointIsNotAnApplication() {
        assertFalse(Application.class.isAssignableFrom(Main.class),
                "Main holds main() and must not extend Application, or JavaFX refuses to start "
                        + "whenever its modules arrive on the classpath - which is how every IDE "
                        + "runs a class.");
    }

    @Test
    @DisplayName("Main still has a usable main method")
    void mainMethodIsPresent() throws Exception {
        Method main = Main.class.getDeclaredMethod("main", String[].class);
        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    @DisplayName("the JavaFX application lives in its own class")
    void applicationClassExists() {
        assertTrue(Application.class.isAssignableFrom(AsisApplication.class),
                "AsisApplication is what Main launches; it has to be the Application");
    }

    /**
     * jpackage names the entry point in the POM. Pointing it at the Application
     * subclass would reintroduce the same failure in the installed build, where
     * nobody is watching a console.
     */
    @Test
    @DisplayName("the packaging configuration launches the entry point, not the Application")
    void packagingPointsAtTheEntryPoint() throws IOException {
        String pom = new String(Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);

        assertTrue(pom.contains("<mainClass>com.asosiaciondeasis.animalesdeasis.Main</mainClass>"),
                "the POM should launch Main");
        assertFalse(pom.contains("AsisApplication</mainClass>"),
                "launching the Application subclass directly is what this separation avoids");
    }
}
