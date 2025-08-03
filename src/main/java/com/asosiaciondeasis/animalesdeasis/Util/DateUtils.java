package com.asosiaciondeasis.animalesdeasis.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;


public class DateUtils {

    public static String convertToIsoFormat(LocalDate date) {
        /**
         * Converts DD-MM-YYYY → YYYY-MM-DD
         * Becasuse is better in this case for the user to insert the date in DD-MM-YYYY
         * */
        return (date != null)
                ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : null;
    }

    public static String convertToIsoFormat(String date) throws ParseException {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date parsedDate = inputFormat.parse(date);
        return outputFormat.format(parsedDate);
    }

}
