package com.asosiaciondeasis.animalesdeasis.Controller.Vaccine;

import com.asosiaciondeasis.animalesdeasis.Util.Helpers.DatePickers;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.FieldValidation;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;

import java.time.LocalDate;

/** The rules the create and edit vaccine forms share. */
final class VaccineForm {

    static final int MAX_NAME_LENGTH = 100;

    private VaccineForm() {
    }

    static void configureDatePicker(DatePicker picker) {
        DatePickers.useDisplayFormat(picker);
        DatePickers.disableFutureDates(picker);
    }

    /**
     * Marks whatever is wrong in place, under the field that caused it, and
     * focuses the first one.
     *
     * @return whether the form is valid
     */
    static boolean validate(FieldValidation validation, TextField nameField, DatePicker datePicker) {
        validation.clear();
        boolean valid = true;

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            validation.reject(nameField, "El nombre de la vacuna es obligatorio");
            valid = false;
        } else if (name.length() > MAX_NAME_LENGTH) {
            validation.reject(nameField,
                    "No puede exceder " + MAX_NAME_LENGTH + " caracteres (van " + name.length() + ")");
            valid = false;
        }

        LocalDate date = datePicker.getValue();
        if (date == null) {
            validation.reject(datePicker, "La fecha de vacunación es obligatoria");
            valid = false;
        } else if (date.isAfter(LocalDate.now())) {
            validation.reject(datePicker, "La fecha no puede ser futura");
            valid = false;
        }

        if (!valid) {
            validation.focusFirstError();
        }
        return valid;
    }
}
