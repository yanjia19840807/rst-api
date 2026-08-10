package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Java equivalent of Excel NETWORKDAYS.INTL(year start, year end, weekend, holidays).
 */
@Component
public class WorkingDaysCalculator {

    /**
     * Counts working days in a calendar year excluding weekends and non-working holidays.
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
     * Whether the date is a working day under weekend + holiday rules.
     *
     * @param date date to test
     * @param weekendCode weekend pattern
     * @param holidays non-working holiday dates
     * @return true when the date is a working day
     */
    public boolean isWorkingDay(LocalDate date, String weekendCode, Collection<LocalDate> holidays) {
        Objects.requireNonNull(date, "date");
        WeekendCode weekend = WeekendCode.parse(weekendCode);
        if (weekend.days().contains(date.getDayOfWeek())) {
            return false;
        }
        if (holidays != null && holidays.contains(date)) {
            return false;
        }
        return true;
    }
}
