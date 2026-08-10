package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Weekend pattern codes used by Team/Calendar and NETWORKDAYS-style calculations.
 */
public enum WeekendCode {
    SAT_SUN(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
    SUN_ONLY(EnumSet.of(DayOfWeek.SUNDAY)),
    FRI_SAT(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
    NONE(EnumSet.noneOf(DayOfWeek.class));

    private final Set<DayOfWeek> days;

    WeekendCode(Set<DayOfWeek> days) {
        this.days = days;
    }

    public Set<DayOfWeek> days() {
        return days;
    }

    /**
     * Parses a stored weekend code; unknown values default to SAT_SUN.
     *
     * @param raw stored code
     * @return weekend code
     */
    public static WeekendCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SAT_SUN;
        }
        try {
            return WeekendCode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SAT_SUN;
        }
    }
}
