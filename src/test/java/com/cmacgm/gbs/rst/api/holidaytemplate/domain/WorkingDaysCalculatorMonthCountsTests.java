package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WorkingDaysCalculator.MonthDayCounts;
import org.junit.jupiter.api.Test;

class WorkingDaysCalculatorMonthCountsTests {

    private final WorkingDaysCalculator calculator = new WorkingDaysCalculator();

    @Test
    void countsWorkdaysAndWeekendsForMonthWithoutHolidays() {
        MonthDayCounts counts = calculator.countMonth(YearMonth.of(2026, 8), "SAT_SUN", List.of());
        // Aug 2026: 31 days, 5 Sat + 5 Sun = 10 weekend days, 21 weekdays
        assertThat(counts.weekendDays()).isEqualTo(10);
        assertThat(counts.workDays()).isEqualTo(21);
    }

    @Test
    void weekdayHolidayReducesWorkdaysOnly() {
        MonthDayCounts counts = calculator.countMonth(
                YearMonth.of(2026, 8),
                "SAT_SUN",
                List.of(LocalDate.of(2026, 8, 10))); // Monday
        assertThat(counts.weekendDays()).isEqualTo(10);
        assertThat(counts.workDays()).isEqualTo(20);
    }
}
