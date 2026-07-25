package com.asosiaciondeasis.animalesdeasis.Util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    @Test
    void localDateRoundTripsThroughUtcString() {
        LocalDate original = LocalDate.of(2024, 3, 21);
        String utc = DateUtils.localDateToUtcString(original);
        assertEquals("2024-03-21T00:00:00", utc);
        assertEquals(original, DateUtils.utcStringToLocalDate(utc));
    }

    @Test
    void nullAndBlankInputsAreHandledGracefully() {
        assertNull(DateUtils.localDateToUtcString(null));
        assertNull(DateUtils.utcStringToLocalDate(null));
        assertNull(DateUtils.utcStringToLocalDate("   "));
    }

    @Test
    void formatUtcForDisplayUsesDayMonthYear() {
        assertEquals("21-03-2024", DateUtils.formatUtcForDisplay("2024-03-21T00:00:00"));
    }

    @Test
    void formatUtcForDisplayFallsBackWhenMissing() {
        assertEquals("Sin información", DateUtils.formatUtcForDisplay(null));
        assertEquals("Sin información", DateUtils.formatUtcForDisplay(""));
    }
}
