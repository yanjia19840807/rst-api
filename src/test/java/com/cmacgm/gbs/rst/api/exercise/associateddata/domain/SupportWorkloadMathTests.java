package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SupportWorkloadMathTests {

    @Test
    void dailyMultiplierRequiresWorkingDays() {
        assertThat(SupportWorkloadMath.annualMultiplier("DAILY", new BigDecimal("250")))
                .isEqualByComparingTo("250");
        assertThat(SupportWorkloadMath.annualMultiplier("DAILY", null)).isNull();
        assertThat(SupportWorkloadMath.annualMultiplier("DAILY", BigDecimal.ZERO)).isNull();
    }

    @Test
    void weeklyAndMonthlyDoNotUseWorkingDays() {
        assertThat(SupportWorkloadMath.annualMultiplier("WEEKLY", null)).isEqualByComparingTo("52");
        assertThat(SupportWorkloadMath.annualMultiplier("MONTHLY", null)).isEqualByComparingTo("12");
    }

    @Test
    void unknownFrequencyIsRejected() {
        assertThatThrownBy(() -> SupportWorkloadMath.requireFrequency("YEARLY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fteAnnualHoursIsNullWhenTeamSetupIsIncomplete() {
        assertThat(SupportWorkloadMath.fteAnnualHours(null, new BigDecimal("261"))).isNull();
        ExerciseTeamSetup empty = ExerciseTeamSetup.emptyShell(UUID.randomUUID(), "u", Instant.now());
        assertThat(SupportWorkloadMath.fteAnnualHours(empty, new BigDecimal("261"))).isNull();
        assertThat(SupportWorkloadMath.teamSetupComplete(empty, new BigDecimal("261"))).isFalse();
    }

    @Test
    void fteAnnualHoursUsesBrdFormulaNot2080() {
        ExerciseTeamSetup setup = completeSetup();
        BigDecimal workingDays = new BigDecimal("261");
        // 8h × 0.85 × 261 × 1.0 = 1774.8
        assertThat(SupportWorkloadMath.fteAnnualHours(setup, workingDays))
                .isEqualByComparingTo("1774.800000");
    }

    @Test
    void totalSupportFteIsZeroWhenThereAreNoItems() {
        assertThat(SupportWorkloadMath.totalSupportFte(
                List.of(), completeSetup(), new BigDecimal("261"))).isEqualByComparingTo("0");
    }

    @Test
    void totalSupportFteIsNullWhenTeamSetupIsIncomplete() {
        ExerciseProductionSupportItem item = ExerciseProductionSupportItem.create(
                UUID.randomUUID(),
                null,
                "Admin",
                "Mail",
                "WEEKLY",
                BigDecimal.ONE,
                "Cases",
                new BigDecimal("30"),
                null,
                "u",
                Instant.now());
        assertThat(SupportWorkloadMath.totalSupportFte(
                List.of(item),
                ExerciseTeamSetup.emptyShell(UUID.randomUUID(), "u", Instant.now()),
                new BigDecimal("261"))).isNull();
    }

    private static ExerciseTeamSetup completeSetup() {
        ExerciseTeamSetup setup = ExerciseTeamSetup.emptyShell(UUID.randomUUID(), "u", Instant.now());
        setup.replaceInputs(new ExerciseTeamSetup.TeamSetupInput(
                null, null, null, null, null, null,
                new BigDecimal("0.85"),
                null,
                null,
                null,
                null,
                null,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                null,
                null,
                null,
                "1"), "u", Instant.now());
        return setup;
    }
}
