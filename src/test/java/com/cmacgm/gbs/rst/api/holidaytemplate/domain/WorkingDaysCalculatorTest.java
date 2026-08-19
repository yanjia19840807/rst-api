package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.MonthDayCounts;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.VolumeDayFlags;
import org.junit.jupiter.api.Test;

class WorkingDaysCalculatorTest {

    private final WorkingDaysCalculator calculator = new WorkingDaysCalculator();

    @Test
    void networkDays_weekendOnly_satSun_2025() {
        assertEquals(261, calculator.networkDays(2025, "1", List.of()));
        assertEquals(261, calculator.networkDays(2025, "SAT_SUN", List.of()));
    }

    @Test
    void networkDays_withChinaDemoHolidays_2025() {
        Set<LocalDate> holidays = Set.of(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 28),
                LocalDate.of(2025, 1, 29),
                LocalDate.of(2025, 1, 30),
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 2, 3),
                LocalDate.of(2025, 2, 4),
                LocalDate.of(2025, 4, 4),
                LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 5, 2),
                LocalDate.of(2025, 5, 5),
                LocalDate.of(2025, 6, 2),
                LocalDate.of(2025, 10, 1),
                LocalDate.of(2025, 10, 2),
                LocalDate.of(2025, 10, 3),
                LocalDate.of(2025, 10, 6),
                LocalDate.of(2025, 10, 7),
                LocalDate.of(2025, 10, 8));
        assertEquals(243, calculator.networkDays(2025, "1", holidays));
    }

    @Test
    void isWorkingDay_respectsWeekendAndHoliday() {
        assertFalse(calculator.isWorkingDay(
                LocalDate.of(2025, 1, 4), "1", List.of())); // Saturday
        assertTrue(calculator.isWorkingDay(
                LocalDate.of(2025, 1, 2), "1", List.of())); // Thursday
        assertFalse(calculator.isWorkingDay(
                LocalDate.of(2025, 1, 1), "1", List.of(LocalDate.of(2025, 1, 1))));
    }

    @Test
    void volumeDay_followsExcelPhDatesTypes() {
        LocalDate saturday = LocalDate.of(2025, 1, 4);
        LocalDate thursday = LocalDate.of(2025, 1, 2);

        VolumeDayFlags holiday = calculator.volumeDay(thursday, "1", HolidayDayKind.HOLIDAY);
        assertTrue(holiday.publicHoliday());
        assertFalse(holiday.workingDay());

        VolumeDayFlags extraRest = calculator.volumeDay(saturday, "1", HolidayDayKind.WEEKEND);
        assertTrue(extraRest.publicHoliday());
        assertFalse(extraRest.workingDay());

        VolumeDayFlags makeup = calculator.volumeDay(saturday, "1", HolidayDayKind.NORMAL);
        assertFalse(makeup.publicHoliday());
        assertTrue(makeup.workingDay());

        VolumeDayFlags unlistedSaturday = calculator.volumeDay(saturday, "1", null);
        assertFalse(unlistedSaturday.publicHoliday());
        assertFalse(unlistedSaturday.workingDay());
    }

    @Test
    void countMonth_ignoresMakeupSaturdaysAndSubtractsWeekdayRest() {
        YearMonth august = YearMonth.of(2026, 8);
        MonthDayCounts base = calculator.countMonth(august, "1", List.of());
        assertEquals(21, base.workDays());
        assertEquals(10, base.weekendDays());

        // Normal (makeup) dates are omitted from NETWORKDAYS holidays, so Saturday stays weekend.
        MonthDayCounts weekdayHoliday = calculator.countMonth(
                august, "1", List.of(LocalDate.of(2026, 8, 10)));
        assertEquals(20, weekdayHoliday.workDays());
        assertEquals(10, weekdayHoliday.weekendDays());
    }
}
