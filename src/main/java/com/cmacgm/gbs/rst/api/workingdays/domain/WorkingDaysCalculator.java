package com.cmacgm.gbs.rst.api.workingdays.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Java equivalent of Excel NETWORKDAYS.INTL and PH Dates volume-day flags.
 */
@Component
public class WorkingDaysCalculator {

    /**
     * Counts working days in a calendar year excluding weekends and non-working holidays.
     *
     * <p>Team Setup year working days pass an empty holiday list (Excel Input C24).
     * Monthly WorkDays pass Holiday + Weekend dates only (Excel Public Holidays pivot).
     *
     * @param year calendar year
     * @param weekendCode weekend pattern
     * @param holidays holiday dates; dates with working-day override should be omitted by caller
     * @return working day count
     */
    public int networkDays(int year, String weekendCode, Collection<LocalDate> holidays) {
        WeekendCode weekend = WeekendCode.parse(weekendCode);
        Set<LocalDate> nonWorking = new HashSet<>();
        if (holidays != null) {
            for (LocalDate holiday : holidays) {
                if (holiday != null && holiday.getYear() == year) {
                    nonWorking.add(holiday);
                }
            }
        }
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        int count = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (!weekend.days().contains(day.getDayOfWeek()) && !nonWorking.contains(day)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether the date is a working day under weekend + rest-holiday rules (no Normal override).
     *
     * @param date date to test
     * @param weekendCode weekend pattern
     * @param holidays non-working holiday dates
     * @return true when the date is a working day
     */
    public boolean isWorkingDay(LocalDate date, String weekendCode, Collection<LocalDate> holidays) {
        return volumeDay(date, weekendCode, null, holidays).workingDay();
    }

    /**
     * Excel Volume per Day flags for one date.
     *
     * <ul>
     *   <li>Type Holiday or Weekend → public holiday, not working</li>
     *   <li>Type Normal → not a public holiday, is working (makeup)</li>
     *   <li>Unlisted → Team Setup weekend pattern only</li>
     * </ul>
     *
     * @param date date to test
     * @param weekendCode Team Setup weekend code
     * @param kind PH Dates type for this date; null when unlisted
     * @return volume-day flags
     */
    public VolumeDayFlags volumeDay(LocalDate date, String weekendCode, HolidayDayKind kind) {
        return volumeDay(date, weekendCode, kind, null);
    }

    private VolumeDayFlags volumeDay(
            LocalDate date,
            String weekendCode,
            HolidayDayKind kind,
            Collection<LocalDate> restDates) {
        Objects.requireNonNull(date, "date");
        if (kind != null && kind.isRestDay()) {
            return new VolumeDayFlags(true, false);
        }
        if (kind == HolidayDayKind.NORMAL) {
            return new VolumeDayFlags(false, true);
        }
        WeekendCode weekend = WeekendCode.parse(weekendCode);
        if (weekend.days().contains(date.getDayOfWeek())) {
            return new VolumeDayFlags(false, false);
        }
        if (restDates != null && restDates.contains(date)) {
            return new VolumeDayFlags(true, false);
        }
        return new VolumeDayFlags(false, true);
    }

    /**
     * Counts workdays and weekend days in a calendar month for Forecast exogenous features.
     *
     * <p>Workdays follow NETWORKDAYS.INTL: weekends plus rest dates (Holiday + Weekend types).
     * Makeup (Normal) Saturdays are not added back. WeekendDays = TotalDays − WorkDays
     * (Excel Volume per Month K), so a weekday rest date increases WeekendDays.
     *
     * @param month year-month
     * @param weekendCode weekend pattern
     * @param nonWorkingHolidays holiday dates that are not working-day overrides
     * @return monthly day counts
     */
    public MonthDayCounts countMonth(
            YearMonth month, String weekendCode, Collection<LocalDate> nonWorkingHolidays) {
        Objects.requireNonNull(month, "month");
        WeekendCode weekend = WeekendCode.parse(weekendCode);
        Set<LocalDate> holidays = new HashSet<>();
        if (nonWorkingHolidays != null) {
            for (LocalDate holiday : nonWorkingHolidays) {
                if (holiday != null && YearMonth.from(holiday).equals(month)) {
                    holidays.add(holiday);
                }
            }
        }
        int workDays = 0;
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (!weekend.days().contains(day.getDayOfWeek()) && !holidays.contains(day)) {
                workDays++;
            }
        }
        return new MonthDayCounts(workDays, month.lengthOfMonth() - workDays);
    }

    /** Workday / weekend-day counts for one calendar month. */
    public record MonthDayCounts(int workDays, int weekendDays) {
    }

    /** Excel Volume per Day: public holiday and working-day flags. */
    public record VolumeDayFlags(boolean publicHoliday, boolean workingDay) {
    }
}
