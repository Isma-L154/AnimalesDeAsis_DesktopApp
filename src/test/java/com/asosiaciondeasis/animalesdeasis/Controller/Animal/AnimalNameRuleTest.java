package com.asosiaciondeasis.animalesdeasis.Controller.Animal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name rule used to be ASCII-only, so ordinary Spanish names such as "Toño"
 * or "Muñeca" could not be saved at all.
 */
class AnimalNameRuleTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "Firulais", "Toño", "Muñeca", "Ñato", "Canelá", "Rex 2"})
    void acceptsLettersInAnyAlphabetDigitsAndSpaces(String name) {
        assertTrue(AnimalFormController.isValidName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rex!", "<b>Max</b>", "Luna;", "Max@home"})
    void rejectsSymbols(String name) {
        assertFalse(AnimalFormController.isValidName(name));
    }
}
