package com.asosiaciondeasis.animalesdeasis.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;


public class DateUtils {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Convert LocalDate to UTC string for DB
    public static String localDateToUtcString(LocalDate date) {
        if (date == null) return null;
        return date.atStartOfDay(UTC).format(UTC_FORMAT);
    }

    // Convert UTC string from DB to LocalDate
    public static LocalDate utcStringToLocalDate(String utcString) {
        if (utcString == null || utcString.isBlank()) return null;
        return LocalDateTime.parse(utcString, UTC_FORMAT).toLocalDate();
    }

    // Format UTC string for display
    public static String formatUtcForDisplay(String utcString) {
        LocalDate date = utcStringToLocalDate(utcString);
        return (date != null) ? date.format(DISPLAY_FORMAT) : "Sin información";
    }
}
