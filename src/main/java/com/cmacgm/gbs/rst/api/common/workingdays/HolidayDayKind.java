package com.cmacgm.gbs.rst.api.common.workingdays;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Excel PH Dates {@code Type}: Holiday (official rest), Weekend (extra rest in a
 * holiday block), Normal (makeup working day).
 */
public enum HolidayDayKind {
    HOLIDAY,
    WEEKEND,
    NORMAL;

    /**
     * Rest days are treated as public holidays for volume (not working).
     *
     * @return true for Holiday and Weekend types
     */
    public boolean isRestDay() {
        return this == HOLIDAY || this == WEEKEND;
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Validates a write payload. Only Excel types are accepted.
     *
     * @param raw requested type
     * @return kind
     * @throws IllegalArgumentException when the type is not Holiday / Weekend / Normal
     */
    @JsonCreator
    public static HolidayDayKind require(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Holiday type is required.");
        }
        String token = raw.trim().toUpperCase(Locale.ROOT);
        return switch (token) {
            case "HOLIDAY" -> HOLIDAY;
            case "WEEKEND" -> WEEKEND;
            case "NORMAL" -> NORMAL;
            default -> throw new IllegalArgumentException(
                    "Holiday type must be HOLIDAY, WEEKEND, or NORMAL.");
        };
    }
}
