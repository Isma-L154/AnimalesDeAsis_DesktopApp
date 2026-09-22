package com.asosiaciondeasis.animalesdeasis.Util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Converts between the dates the forms work with and the ISO 8601 strings
 * ({@code yyyy-MM-ddTHH:mm:ss}) stored in SQLite and Firestore.
 */
public final class DateUtils {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private DateUtils() {
    }

    public static String localDateToUtcString(LocalDate date) {
        if (date == null) return null;
        return date.atStartOfDay(UTC).format(UTC_FORMAT);
    }

    public static LocalDate utcStringToLocalDate(String utcString) {
        if (utcString == null || utcString.isBlank()) return null;
        return LocalDateTime.parse(utcString, UTC_FORMAT).toLocalDate();
    }

    public static String formatUtcForDisplay(String utcString) {
        LocalDate date = utcStringToLocalDate(utcString);
        return (date != null) ? date.format(DISPLAY_FORMAT) : "Sin información";
    }
}
