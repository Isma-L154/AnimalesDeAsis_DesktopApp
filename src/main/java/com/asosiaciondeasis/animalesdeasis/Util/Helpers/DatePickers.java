package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** The date format and limits every form in the application shares. */
public final class DatePickers {

    private static final String PATTERN = "dd-MM-yyyy";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern(PATTERN);
    private static final String DISABLED_CLASS = "date-cell-disabled";

    private DatePickers() {
    }

    /**
     * Shows and parses dates as {@code dd-MM-yyyy}. Text that does not parse
     * clears the value instead of throwing, so the form's own validation reports
     * it as missing.
     */
    public static void useDisplayFormat(DatePicker picker) {
        picker.setPromptText(PATTERN);
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? date.format(FORMAT) : "";
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return LocalDate.parse(text.trim(), FORMAT);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
    }

    /** Greys out and blocks every day after today. */
    public static void disableFutureDates(DatePicker picker) {
        picker.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                // DatePicker recycles its cells as the user pages through months, so
                // every branch has to set the state explicitly. Setting it only in the
                // "future date" case left a disabled day styled and unclickable once
                // its cell was reused for a perfectly valid past date. The null check
                // matters too: an emptied cell arrives here with a null date.
                boolean isFuture = !empty && date != null && date.isAfter(LocalDate.now());

                setDisable(isFuture);
                getStyleClass().remove(DISABLED_CLASS);
                if (isFuture) {
                    getStyleClass().add(DISABLED_CLASS);
                }
                setTooltip(isFuture ? new Tooltip("No se pueden seleccionar fechas futuras") : null);
            }
        });
    }
}
