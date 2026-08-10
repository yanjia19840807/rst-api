package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WorkingDaysCalculatorTest {

    private final WorkingDaysCalculator calculator = new WorkingDaysCalculator();

    @Test
    void networkDays_weekendOnly_satSun_2025() {
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
        assertEquals(243, calculator.networkDays(2025, "SAT_SUN", holidays));
    }

    @Test
    void isWorkingDay_respectsWeekendAndHoliday() {
        assertFalse(calculator.isWorkingDay(
                LocalDate.of(2025, 1, 4), "SAT_SUN", List.of())); // Saturday
        assertTrue(calculator.isWorkingDay(
                LocalDate.of(2025, 1, 2), "SAT_SUN", List.of())); // Thursday
        assertFalse(calculator.isWorkingDay(
                LocalDate.of(2025, 1, 1), "SAT_SUN", List.of(LocalDate.of(2025, 1, 1))));
    }
}
