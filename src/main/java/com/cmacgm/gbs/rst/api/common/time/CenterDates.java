package com.cmacgm.gbs.rst.api.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Instant → civil date in a Center timezone. Storage stays UTC Instant.
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
