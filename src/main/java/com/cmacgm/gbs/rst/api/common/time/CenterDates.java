package com.cmacgm.gbs.rst.api.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Instant → Center-local civil date or datetime. Storage stays UTC Instant.
 */
public final class CenterDates {

    private CenterDates() {
    }

    /**
     * @param instant event time; null stays null
     * @param center owning Center
     * @return calendar date in that Center
     */
    public static LocalDate dateOf(Instant instant, String center) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(CenterZones.of(center)).toLocalDate();
    }

    /**
     * @param instant event time; null stays null
     * @param center owning Center
     * @return wall-clock datetime in that Center
     */
    public static LocalDateTime dateTimeOf(Instant instant, String center) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(CenterZones.of(center)).toLocalDateTime();
    }

    /**
     * Center-local civil datetime ({@code yyyy-MM-dd'T'HH:mm:ss}) for list and export.
     *
     * @param instant event time
     * @param center owning Center
     * @return formatted datetime, or empty when either side is missing
     */
    public static String civilDateTime(Instant instant, String center) {
        LocalDateTime value = dateTimeOf(instant, center);
        if (value == null) {
            return "";
        }
        return value.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Reads a Center-local civil date or datetime. Instant strings are not converted here.
     *
     * @param value {@code yyyy-MM-dd} or {@code yyyy-MM-dd'T'HH:mm[:ss]}
     * @return calendar date, or null when blank / unparseable
     */
    public static LocalDate civilDateOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() >= 10) {
                return LocalDate.parse(trimmed.substring(0, 10));
            }
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Whole Center-local calendar days between two instants, never negative.
     *
     * @param from start instant
     * @param to end instant
     * @param center owning Center
     * @return elapsed days, or 0 when either instant is missing
     */
    public static int daysBetween(Instant from, Instant to, String center) {
        if (from == null || to == null) {
            return 0;
        }
        ZoneId zone = CenterZones.of(center);
        long days = ChronoUnit.DAYS.between(
                from.atZone(zone).toLocalDate(),
                to.atZone(zone).toLocalDate());
        return (int) Math.max(0, days);
    }
}
