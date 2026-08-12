package com.cmacgm.gbs.rst.api.common.time;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Helpers for month-grain keys stored as DATE (first day of month) and exchanged as YYYY-MM.
 */
public final class MonthKeys {

    private MonthKeys() {
    }

    /** Parses YYYY-MM into the first day of that month. */
    public static LocalDate parseMonthStart(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            throw new IllegalArgumentException("month is required");
        }
        try {
            return YearMonth.parse(yearMonth.trim()).atDay(1);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("month must be YYYY-MM", ex);
        }
    }

    /** Formats a month-start DATE as YYYY-MM. */
    public static String formatYearMonth(LocalDate monthStart) {
        if (monthStart == null) {
            return null;
        }
        return YearMonth.from(monthStart).toString();
    }

    /** Normalizes any date to the first day of its month. */
    public static LocalDate monthStart(LocalDate date) {
        return date == null ? null : date.withDayOfMonth(1);
    }

    public static LocalDate monthStart(YearMonth yearMonth) {
        return yearMonth == null ? null : yearMonth.atDay(1);
    }
}
