package com.cmacgm.gbs.rst.api.workingdays.domain;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Excel {@code NETWORKDAYS.INTL} weekend codes (1–7 and 11–17).
 */
public enum WeekendCode {
    SATURDAY_SUNDAY(1, "Saturday, Sunday", EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
    SUNDAY_MONDAY(2, "Sunday, Monday", EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)),
    MONDAY_TUESDAY(3, "Monday, Tuesday", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)),
    TUESDAY_WEDNESDAY(4, "Tuesday, Wednesday", EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)),
    WEDNESDAY_THURSDAY(5, "Wednesday, Thursday", EnumSet.of(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)),
    THURSDAY_FRIDAY(6, "Thursday, Friday", EnumSet.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
    FRIDAY_SATURDAY(7, "Friday, Saturday", EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
    SUNDAY_ONLY(11, "Sunday", EnumSet.of(DayOfWeek.SUNDAY)),
    MONDAY_ONLY(12, "Monday", EnumSet.of(DayOfWeek.MONDAY)),
    TUESDAY_ONLY(13, "Tuesday", EnumSet.of(DayOfWeek.TUESDAY)),
    WEDNESDAY_ONLY(14, "Wednesday", EnumSet.of(DayOfWeek.WEDNESDAY)),
    THURSDAY_ONLY(15, "Thursday", EnumSet.of(DayOfWeek.THURSDAY)),
    FRIDAY_ONLY(16, "Friday", EnumSet.of(DayOfWeek.FRIDAY)),
    SATURDAY_ONLY(17, "Saturday", EnumSet.of(DayOfWeek.SATURDAY));

    /** Excel / stored default: Saturday + Sunday. */
    public static final String DEFAULT_STORED = "1";

    private static final Map<Integer, WeekendCode> BY_NUMBER = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(WeekendCode::excelNumber, Function.identity()));

    private final int excelNumber;
    private final String daysLabel;
    private final Set<DayOfWeek> days;

    WeekendCode(int excelNumber, String daysLabel, Set<DayOfWeek> days) {
        this.excelNumber = excelNumber;
        this.daysLabel = daysLabel;
        this.days = days;
    }

    public int excelNumber() {
        return excelNumber;
    }

    public String daysLabel() {
        return daysLabel;
    }

    /** Value persisted on Team Setup ({@code "1"} … {@code "17"}). */
    public String storedValue() {
        return Integer.toString(excelNumber);
    }

    public Set<DayOfWeek> days() {
        return days;
    }

    /**
     * Parses a stored weekend code. Accepts Excel numbers and legacy RST names.
     * Unknown or blank values are rejected — they must not become Saturday + Sunday.
     *
     * @param raw stored code
     * @return weekend code
     * @throws IllegalArgumentException when the code is missing or not recognized
     */
    public static WeekendCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Weekend code is required.");
        }
        String trimmed = raw.trim();
        try {
            int number = Integer.parseInt(trimmed);
            WeekendCode byNumber = BY_NUMBER.get(number);
            if (byNumber != null) {
                return byNumber;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to name aliases.
        }
        String token = trimmed.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (token) {
            case "SAT_SUN", "SATURDAY_SUNDAY" -> SATURDAY_SUNDAY;
            case "SUN_ONLY", "SUNDAY_ONLY" -> SUNDAY_ONLY;
            case "FRI_SAT", "FRIDAY_SATURDAY" -> FRIDAY_SATURDAY;
            case "SUNDAY_MONDAY" -> SUNDAY_MONDAY;
            case "MONDAY_TUESDAY" -> MONDAY_TUESDAY;
            case "TUESDAY_WEDNESDAY" -> TUESDAY_WEDNESDAY;
            case "WEDNESDAY_THURSDAY" -> WEDNESDAY_THURSDAY;
            case "THURSDAY_FRIDAY" -> THURSDAY_FRIDAY;
            case "MONDAY_ONLY" -> MONDAY_ONLY;
            case "TUESDAY_ONLY" -> TUESDAY_ONLY;
            case "WEDNESDAY_ONLY" -> WEDNESDAY_ONLY;
            case "THURSDAY_ONLY" -> THURSDAY_ONLY;
            case "FRIDAY_ONLY" -> FRIDAY_ONLY;
            case "SATURDAY_ONLY" -> SATURDAY_ONLY;
            default -> throw new IllegalArgumentException(
                    "Weekend code must be an Excel NETWORKDAYS.INTL code (1–7 or 11–17).");
        };
    }

    /**
     * Normalizes any accepted input to the stored Excel number string.
     *
     * @param raw stored or legacy code
     * @return Excel number as string
     */
    public static String storedValue(String raw) {
        return parse(raw).storedValue();
    }
}
