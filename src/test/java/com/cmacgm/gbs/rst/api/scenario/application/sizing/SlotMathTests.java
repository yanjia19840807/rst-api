package com.cmacgm.gbs.rst.api.scenario.application.sizing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

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
}
