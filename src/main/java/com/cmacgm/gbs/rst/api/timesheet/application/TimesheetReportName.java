package com.cmacgm.gbs.rst.api.timesheet.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Timesheet file-name convention.
 *
 * <p>{@code Daily Report of 20260727(GBS CHINA).xlsx}
 * <p>{@code Monthly Report of 202606(GBS CHINA).xlsx}
 */
public final class TimesheetReportName {

    private static final Pattern DAILY = Pattern.compile(
            "^Daily Report of (\\d{8})\\((.+)\\)\\.xlsx$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHLY = Pattern.compile(
            "^Monthly Report of (\\d{6})\\((.+)\\)\\.xlsx$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String REGION = "GBS CHINA";

    private TimesheetReportName() {
    }

    /**
     * Parsed file identity.
     *
     * @param kind DAILY or MONTHLY
     * @param syncDate business date (month-end for Monthly)
     * @param region parenthesis text
     */
    public record Parsed(String kind, LocalDate syncDate, String region) {
    }

    /**
     * Parses a report file name.
     *
     * @param fileName original name
     * @return parsed identity
     */
    public static Optional<Parsed> parse(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }
        String name = fileName.trim();
        Matcher daily = DAILY.matcher(name);
        if (daily.matches()) {
            LocalDate date = parseDay(daily.group(1));
            String region = daily.group(2).trim();
            if (date == null || !REGION.equalsIgnoreCase(region)) {
                return Optional.empty();
            }
            return Optional.of(new Parsed("DAILY", date, region));
        }
        Matcher monthly = MONTHLY.matcher(name);
        if (monthly.matches()) {
            LocalDate date = parseMonthEnd(monthly.group(1));
            String region = monthly.group(2).trim();
            if (date == null || !REGION.equalsIgnoreCase(region)) {
                return Optional.empty();
            }
            return Optional.of(new Parsed("MONTHLY", date, region));
        }
        return Optional.empty();
    }

    private static LocalDate parseDay(String compact) {
        try {
            return LocalDate.parse(compact, DAY);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDate parseMonthEnd(String compact) {
        try {
            return YearMonth.parse(compact, YEAR_MONTH).atEndOfMonth();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * Whether the name is a Daily or Monthly report for this kind.
     *
     * @param fileName file name
     * @param kind DAILY or MONTHLY
     * @return true when the name matches the kind
     */
    public static boolean matchesKind(String fileName, String kind) {
        return parse(fileName)
                .filter(parsed -> parsed.kind().equals(kind.trim().toUpperCase(Locale.ROOT)))
                .isPresent();
    }
}
