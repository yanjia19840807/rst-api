package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import org.junit.jupiter.api.Test;

class ExerciseTeamSetupTests {

    @Test
    void averageTenureYearsMatchesExcelInputC10() {
        ExerciseTeamSetup setup = setupWith(
                new BigDecimal("1"),
                new BigDecimal("4"),
                new BigDecimal("1"),
                new BigDecimal("9"),
                null,
                null,
                null);

        assertThat(setup.totalAgents()).isEqualByComparingTo("15");
        // Excel C10: (1×3 + 4×15 + 1×36 + 9×48) / 12 / 15 = 2.95 (displayed as 3.0 at 1 d.p.)
        assertThat(setup.averageTenureYears()).isEqualByComparingTo("2.950000");
    }

    @Test
    void workingHoursAndDailyCapacityUseSlaClock() {
        ExerciseTeamSetup setup = setupWith(
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.8"),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0));

        // SLA 09:00–18:00 → 9h; Daily capacity = 9 × 0.8 × 3600 / 128 = 202.5
        assertThat(setup.workingHoursPerDay()).isEqualByComparingTo("9.000000");
        assertThat(setup.dailyCapacityPerAgent(new BigDecimal("128")))
                .isEqualByComparingTo("202.500000");
    }

    private static ExerciseTeamSetup setupWith(
            BigDecimal lt6,
            BigDecimal m6To24,
            BigDecimal m24To48,
            BigDecimal gt48,
            BigDecimal availability,
            LocalTime slaStart,
            LocalTime slaEnd) {
        ExerciseTeamSetup setup = ExerciseTeamSetup.emptyShell(
                UUID.randomUUID(), "test", Instant.parse("2026-08-20T00:00:00Z"));
        setup.replaceInputs(
                new TeamSetupInput(
                        lt6, m6To24, m24To48, gt48,
                        null, null, availability,
                        null, null, null, null, null, slaStart, slaEnd, null, null, null, "1"),
                "test",
                Instant.parse("2026-08-20T00:00:00Z"));
        return setup;
    }
}
