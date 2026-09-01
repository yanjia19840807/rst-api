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
 * <p>{@code Daily Raw Data of 2026-08-31 - GBS CHINA.xlsx}
 * <p>{@code Monthly Report of 202607(GBS CHINA).xlsx}
 * <p>{@code Monthly Report of 202607 Revision(GBS CHINA).xlsx}
 * <p>{@code Monthly Report of 202607 Revision 1(GBS CHINA).xlsx}
 *
 * <p>A trailing SharePoint/download timestamp is ignored.
 */
public final class TimesheetReportName {

    private static final Pattern DAILY = Pattern.compile(
            "^Daily Raw Data of (\\d{4}-\\d{2}-\\d{2}) - (.+?)(?:\\s+\\d+)?\\.xlsx$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHLY = Pattern.compile(
            "^Monthly Report of (\\d{6})(?: (Revision(?: (\\d+))?))?\\((.+)\\)(?:\\s+\\d+)?\\.xlsx$",
            Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String REGION = "GBS CHINA";

    /** Monthly original (no Revision token). Lower than any Revision. */
    public static final int BASE_REVISION = -1;

    /** {@code Revision} with no number. */
    public static final int FIRST_REVISION = 0;

    private TimesheetReportName() {
    }

    /**
     * Parsed file identity.
     *
     * @param kind DAILY or MONTHLY
     * @param syncDate business date (month-end for Monthly)
     * @param region parenthesis or trailing region text
     * @param revision Monthly revision; {@link #BASE_REVISION} when absent
     */
    public record Parsed(String kind, LocalDate syncDate, String region, int revision) {
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
            LocalDate date = parseIsoDay(daily.group(1));
            String region = daily.group(2).trim();
            if (date == null || !REGION.equalsIgnoreCase(region)) {
                return Optional.empty();
            }
            return Optional.of(new Parsed("DAILY", date, region, BASE_REVISION));
        }
        Matcher monthly = MONTHLY.matcher(name);
        if (monthly.matches()) {
            LocalDate date = parseMonthEnd(monthly.group(1));
            String region = monthly.group(4).trim();
            if (date == null || !REGION.equalsIgnoreCase(region)) {
                return Optional.empty();
            }
            return Optional.of(new Parsed("MONTHLY", date, region, monthlyRevision(monthly)));
        }
        return Optional.empty();
    }

    private static int monthlyRevision(Matcher monthly) {
        if (monthly.group(2) == null) {
            return BASE_REVISION;
        }
        String number = monthly.group(3);
        if (number == null || number.isBlank()) {
            return FIRST_REVISION;
        }
        return Integer.parseInt(number);
    }

    private static LocalDate parseIsoDay(String iso) {
        try {
            return LocalDate.parse(iso, ISO_DAY);
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
