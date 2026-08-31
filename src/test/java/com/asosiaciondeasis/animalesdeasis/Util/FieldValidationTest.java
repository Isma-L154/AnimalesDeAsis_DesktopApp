package com.asosiaciondeasis.animalesdeasis.Util;

import com.asosiaciondeasis.animalesdeasis.JavaFxToolkit;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.FieldValidation;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inline validation, which is what replaced fifty error dialogs.
 *
 * <p>Worth testing because the failure mode is silent. If a message is not
 * inserted, or is not removed on the next attempt, nothing throws — the form
 * simply stops explaining itself, or starts accumulating stale complaints under
 * fields that are now fine.</p>
 */
class FieldValidationTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        JavaFxToolkit.start();
    }

    private static long messagesIn(VBox box) {
        return box.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("field-error-message"))
                .count();
    }

    @Test
    @DisplayName("a rejected field is outlined and gets a message beneath it")
    void rejectMarksAndExplains() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            TextField field = new TextField();
            VBox form = new VBox(field);
            FieldValidation validation = new FieldValidation();

            validation.reject(field, "El nombre es obligatorio");

            assertTrue(field.getStyleClass().contains("field-error"),
                    "the field itself must show it is the problem");
            assertEquals(1, messagesIn(form));
            assertEquals("El nombre es obligatorio",
                    ((Label) form.getChildren().get(1)).getText());
            assertEquals("El nombre es obligatorio", field.getAccessibleHelp(),
                    "a screen reader cannot see the outline");
        });
    }

    @Test
    @DisplayName("the message lands directly under its own field")
    void messageIsPlacedAfterTheField() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            TextField first = new TextField();
            TextField second = new TextField();
            VBox form = new VBox(first, second);
            FieldValidation validation = new FieldValidation();

            validation.reject(first, "Mal");

            // first, message, second - not appended at the end, where it would
            // appear to belong to the wrong field.
            assertEquals(3, form.getChildren().size());
            assertTrue(form.getChildren().get(1).getStyleClass().contains("field-error-message"));
            assertEquals(second, form.getChildren().get(2));
        });
    }

    @Test
    @DisplayName("rejecting twice replaces the message instead of stacking one")
    void rejectingTwiceDoesNotAccumulate() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            TextField field = new TextField();
            VBox form = new VBox(field);
            FieldValidation validation = new FieldValidation();

            validation.reject(field, "Primera razón");
            validation.reject(field, "Segunda razón");

            assertEquals(1, messagesIn(form), "a second attempt must not add a second message");
            assertEquals("Segunda razón", ((Label) form.getChildren().get(1)).getText());
        });
    }

    @Test
    @DisplayName("accepting a field removes both the outline and the message")
    void acceptClearsEverything() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            TextField field = new TextField();
            VBox form = new VBox(field);
            FieldValidation validation = new FieldValidation();

            validation.reject(field, "Mal");
            validation.accept(field);

            assertFalse(field.getStyleClass().contains("field-error"));
            assertEquals(0, messagesIn(form), "a stale complaint under a valid field is worse than none");
            assertEquals(1, form.getChildren().size());
            assertFalse(validation.hasErrors());
        });
    }

    @Test
    @DisplayName("clear resets every field at once, so re-validating starts clean")
    void clearResetsAll() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            TextField a = new TextField();
            TextField b = new TextField();
            VBox form = new VBox(a, b);
            FieldValidation validation = new FieldValidation();

            validation.reject(a, "Mal A");
            validation.reject(b, "Mal B");
            assertTrue(validation.hasErrors());

            validation.clear();

            assertFalse(validation.hasErrors());
            assertEquals(0, messagesIn(form));
            assertFalse(a.getStyleClass().contains("field-error"));
            assertFalse(b.getStyleClass().contains("field-error"));
        });
    }

    @Test
    @DisplayName("require reports validity and marks only what failed")
    void requireReportsAndMarks() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            TextField good = new TextField("valor");
            TextField bad = new TextField();
            VBox form = new VBox(good, bad);
            FieldValidation validation = new FieldValidation();

            assertTrue(validation.requireText(good, good.getText(), "Obligatorio"));
            assertFalse(validation.requireText(bad, bad.getText(), "Obligatorio"));

            assertFalse(good.getStyleClass().contains("field-error"));
            assertTrue(bad.getStyleClass().contains("field-error"));
            assertEquals(1, messagesIn(form));
        });
    }

    /**
     * Forms wrap fields for layout, so the message has to be inserted next to
     * whichever ancestor the containing VBox actually holds, not next to the
     * control itself.
     */
    @Test
    @DisplayName("a wrapped field still gets its message in the right place")
    void handlesFieldsInsideAWrapper() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            TextField field = new TextField();
            HBox wrapper = new HBox(field);
            VBox form = new VBox(wrapper);
            FieldValidation validation = new FieldValidation();

            validation.reject(field, "Mal");

            assertEquals(2, form.getChildren().size());
            assertEquals(wrapper, form.getChildren().get(0));
            assertTrue(form.getChildren().get(1).getStyleClass().contains("field-error-message"));
        });
    }

    @Test
    @DisplayName("a field with nowhere to put a message is still marked")
    void degradesWhenThereIsNoContainer() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            // No VBox anywhere above it: the label has no home.
            TextField orphan = new TextField();
            FieldValidation validation = new FieldValidation();

            validation.reject(orphan, "Mal");

            assertTrue(orphan.getStyleClass().contains("field-error"),
                    "an outlined field with no text beats a message that lands somewhere unrelated");
            assertEquals("Mal", orphan.getAccessibleHelp());
        });
    }
}
