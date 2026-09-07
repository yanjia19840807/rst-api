package com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class SlotMathTests {

    @Test
    void slaSlotLimitCeilsFractionalMinutesToOneSlot() {
        int limit = SlotMath.slaSlotLimit(new BigDecimal("0.5"), 30);
        assertThat(limit).isEqualTo(1);
    }

    @Test
    void applicabilityOnAcceptsFractionalBusinessHoursSla() {
        assertThat(SlotMath.applicabilityOn("BUSINESS_HOURS", new BigDecimal("0.5"))).isTrue();
    }

    @Test
    void withinShiftAcceptsFractionalDuration() {
        assertThat(SlotMath.withinShift(
                java.time.LocalTime.of(9, 0),
                java.time.LocalTime.of(9, 30),
                java.time.LocalTime.of(8, 0),
                new BigDecimal("90.5"))).isTrue();
    }

    @Test
    void shiftDayUsesExcelIntOfSlotMinusStartTime() {
        LocalTime start = LocalTime.of(18, 0);
        assertThat(SlotMath.shiftDay(LocalDateTime.of(2026, 1, 3, 2, 0), start))
                .isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(SlotMath.shiftDay(LocalDateTime.of(2026, 1, 3, 19, 0), start))
                .isEqualTo(LocalDate.of(2026, 1, 3));
    }

    @Test
    void shiftContributionIsZeroOnWeekendOrOutsideShift() {
        BigDecimal hc = new BigDecimal("3");
        assertThat(SlotMath.shiftContribution(false, true, hc)).isEqualByComparingTo("3.000000");
        assertThat(SlotMath.shiftContribution(true, true, hc)).isEqualByComparingTo("0.000000");
        assertThat(SlotMath.shiftContribution(false, false, hc)).isEqualByComparingTo("0.000000");
    }
}
