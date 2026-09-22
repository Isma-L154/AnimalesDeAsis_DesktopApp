package com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals;

/**
 * Raised when an insert or update would give two animals the same chip number
 * or barcode.
 *
 * <p>A type of its own because it is the one storage failure a person can fix
 * from the form, so the interface has to be able to tell it apart from a
 * database that is simply broken.</p>
 */
public class DuplicateChipException extends Exception {

    public DuplicateChipException(Throwable cause) {
        super("chip_number or barcode must be unique", cause);
    }
}
