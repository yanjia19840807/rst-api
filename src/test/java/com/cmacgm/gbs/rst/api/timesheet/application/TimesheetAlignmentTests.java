package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TimesheetAlignmentTests {

    private static final TimesheetAlignment.Key LINE_A =
            new TimesheetAlignment.Key("CMA CGM", "Sydney", "Australia");
    private static final TimesheetAlignment.Key LINE_B =
            new TimesheetAlignment.Key("ANL", "Melbourne", "Australia");

    @Test
    void hcChangeAloneIsNotStructuralDrift() {
        TimesheetAlignment alignment = TimesheetAlignment.evaluate(
                true,
                LocalDate.of(2026, 8, 31),
                List.of(LINE_A),
                Map.of(LINE_A, new BigDecimal("11.8")));

        assertThat(alignment.structuralDrift()).isFalse();
        assertThat(alignment.outOfScope()).isFalse();
        assertThat(alignment.lineMissing(LINE_A.carrier(), LINE_A.site(), LINE_A.customerCountry()))
                .isFalse();
        assertThat(alignment.currentDeliveryHc()).isEqualByComparingTo("11.8");
    }

    @Test
    void missingKpiLineIsStructuralDrift() {
        TimesheetAlignment alignment = TimesheetAlignment.evaluate(
                true,
                LocalDate.of(2026, 8, 31),
                List.of(LINE_A, LINE_B),
                Map.of(LINE_A, new BigDecimal("8.0")));

        assertThat(alignment.structuralDrift()).isTrue();
        assertThat(alignment.lineMissing(LINE_B.carrier(), LINE_B.site(), LINE_B.customerCountry()))
                .isTrue();
        assertThat(alignment.currentDeliveryHc()).isEqualByComparingTo("8.0");
    }

    @Test
    void missingScopeMarksEveryLine() {
        TimesheetAlignment alignment = TimesheetAlignment.evaluate(
                false,
                LocalDate.of(2026, 8, 31),
                List.of(LINE_A),
                Map.of(LINE_A, new BigDecimal("12.5")));

        assertThat(alignment.structuralDrift()).isTrue();
        assertThat(alignment.outOfScope()).isTrue();
        assertThat(alignment.lineMissing(LINE_A.carrier(), LINE_A.site(), LINE_A.customerCountry()))
                .isTrue();
        assertThat(alignment.currentDeliveryHc()).isEqualByComparingTo("0");
    }
}
