package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows why a field is wrong, next to the field.
 *
 * <p>Validation used to be a modal: a failed date raised a dialog saying the
 * date was invalid, and the form behind it looked exactly as it had a moment
 * before. You had to hold the message in your head, dismiss the window, and then
 * work out which of eleven fields it meant. Fifty of the sixty-two dialogs in
 * this application were errors, and most of them were this.</p>
 *
 * <p>Here the message sits under the field that caused it, the field is outlined,
 * and nothing is blocked — the correction happens where you are already looking.</p>
 *
 * <h2>How the message gets a place to live</h2>
 * <p>The error label is inserted into the field's parent immediately after the
 * field, which works because the forms in this application lay fields out
 * vertically inside a {@code VBox}. Where that does not hold, the label is
 * skipped and the outline is still applied: a wrong-looking field with no text
 * beats a message that lands somewhere unrelated.</p>
 */
public final class FieldValidation {

    private static final String ERROR_CLASS = "field-error";
    private static final String MESSAGE_CLASS = "field-error-message";

    /** Error labels created for each field, so they can be removed again. */
    private final Map<Control, Label> messages = new LinkedHashMap<>();

    /**
     * Marks {@code field} as invalid and explains why.
     *
     * <p>Calling it twice for the same field replaces the message rather than
     * stacking a second one underneath.</p>
     */
    public void reject(Control field, String reason) {
        if (field == null) {
            return;
        }
        if (!field.getStyleClass().contains(ERROR_CLASS)) {
            field.getStyleClass().add(ERROR_CLASS);
        }
        // Announced to assistive technology, which cannot see the outline.
        field.setAccessibleHelp(reason);

        Label existing = messages.get(field);
        if (existing != null) {
            existing.setText(reason);
            return;
        }

        Label label = new Label(reason);
        label.getStyleClass().add(MESSAGE_CLASS);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);

        if (insertAfter(field, label)) {
            messages.put(field, label);
        }
    }

    /** Clears the error state of one field. */
    public void accept(Control field) {
        if (field == null) {
            return;
        }
        field.getStyleClass().remove(ERROR_CLASS);
        field.setAccessibleHelp(null);
        Label label = messages.remove(field);
        if (label != null && label.getParent() instanceof Pane parent) {
            parent.getChildren().remove(label);
        }
    }

    /** Clears every field this instance has marked. Call before re-validating. */
    public void clear() {
        for (Control field : Map.copyOf(messages).keySet()) {
            accept(field);
        }
        messages.clear();
    }

    /** Whether anything is currently rejected. */
    public boolean hasErrors() {
        return !messages.isEmpty();
    }

    /**
     * Moves focus to the first field that was rejected, so correcting a form is
     * one keystroke rather than a hunt.
     */
    public void focusFirstError() {
        messages.keySet().stream().findFirst().ifPresent(Control::requestFocus);
    }

    /**
     * Convenience for the common shape: check a condition, and reject with a
     * reason when it does not hold.
     *
     * @return {@code true} when the field is valid
     */
    public boolean require(Control field, boolean condition, String reason) {
        if (condition) {
            accept(field);
            return true;
        }
        reject(field, reason);
        return false;
    }

    private boolean insertAfter(Control field, Label label) {
        Node anchor = field;
        // A DatePicker or a ComboBox is often wrapped for layout, so walk up
        // until the node's parent is something we can insert into.
        while (anchor.getParent() != null && !(anchor.getParent() instanceof VBox)) {
            anchor = anchor.getParent();
        }
        if (!(anchor.getParent() instanceof VBox box)) {
            return false;
        }
        int index = box.getChildren().indexOf(anchor);
        if (index < 0) {
            return false;
        }
        VBox.setMargin(label, new javafx.geometry.Insets(2, 0, 0, 0));
        box.getChildren().add(index + 1, label);
        return true;
    }

    /** Fields whose value is a plain string, for the most common check of all. */
    public boolean requireText(Control field, String value, String reason) {
        return require(field, value != null && !value.isBlank(), reason);
    }

    /** Hides a node without leaving its space behind, used for optional hints. */
    public static void setShown(Region node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
