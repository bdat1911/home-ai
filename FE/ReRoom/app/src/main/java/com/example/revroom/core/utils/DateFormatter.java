package com.example.revroom.core.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateFormatter {
    /**
     * Formats an ISO 8601 date string to the dd/MM/yyyy pattern.
     * Fallback to manual substring slicing is implemented if parsing fails.
     *
     * @param isoString The date string from the backend (e.g., "2026-04-19T14:30:00Z").
     * @return The formatted date string (e.g., "19/04/2026").
     */
    public static String formatToDayMonthYear(String isoString) {
        if (isoString == null || isoString.trim().isEmpty()) {
            return "N/A";
        }

        try {
            // Standard parsing
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(isoString);
            if (date != null) {
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            // Fallback to manual string parsing which is highly safe for typical "YYYY-MM-DD" patterns
        }

        // Extremely safe manual string splitting fallback
        try {
            String datePart = isoString.split("T")[0]; // "yyyy-MM-dd"
            String[] parts = datePart.split("-");
            if (parts.length == 3) {
                String year = parts[0];
                String month = parts[1];
                String day = parts[2];
                return day + "/" + month + "/" + year;
            }
        } catch (Exception e) {
            // Ignore
        }

        return isoString; // Return original string if all else fails
    }
}
