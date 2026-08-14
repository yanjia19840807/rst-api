package com.cmacgm.gbs.rst.api.scenario.application.sizing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SizingMathTests {

    @Test
    void monthlyManualVolumeAppliesAutomation() {
        BigDecimal manual = SizingMath.monthlyManualVolume(
                new BigDecimal("1000"),
                new BigDecimal("0.10"));
        assertThat(manual).isEqualByComparingTo("900.000000");
    }

    @Test
    void nominalHcWithoutOtRoundsUp() {
        BigDecimal hc = SizingMath.nominalHcWithoutOt(
                new BigDecimal("945"),
                new BigDecimal("120"),
                new BigDecimal("22"),
                new BigDecimal("8"),
                new BigDecimal("0.85"),
                new BigDecimal("0.90"));
        assertThat(hc).isEqualByComparingTo("1");
    }

    @Test
    void backlogEndDoesNotGoNegative() {
        BigDecimal end = SizingMath.backlogEnd(
                BigDecimal.ZERO,
                new BigDecimal("50"),
                new BigDecimal("80"),
                new BigDecimal("10"));
        assertThat(end).isEqualByComparingTo("0.000000");
    }

    @Test
    void overtimeCapacityAcceptsFractionalMinutes() {
        BigDecimal overtime = SizingMath.overtimeCapacity(
                true,
                new BigDecimal("2"),
                new BigDecimal("0.5"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("60"));
        assertThat(overtime).isEqualByComparingTo("1.000000");
    }
}
