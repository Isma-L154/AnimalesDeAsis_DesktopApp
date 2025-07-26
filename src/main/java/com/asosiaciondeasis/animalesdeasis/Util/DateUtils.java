package com.asosiaciondeasis.animalesdeasis.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

    public static String convertToIsoFormat(String date) throws ParseException {
        /**
         * Converts DD-MM-YYYY → YYYY-MM-DD
         * Becasuse is better in this case for the user to insert the date in DD-MM-YYYY
         * */
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date parsedDate = inputFormat.parse(date);
        return outputFormat.format(parsedDate);
    }
}
