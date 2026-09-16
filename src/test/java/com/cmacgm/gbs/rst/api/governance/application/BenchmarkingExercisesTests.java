package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.junit.jupiter.api.Test;

class BenchmarkingExercisesTests {

    @Test
    void keepsLatestValidatedPerCenterSupervisorPl3() {
        RstExercise older = exercise(
                "EX-OLD", "GBS CHINA", "175344", "367", Instant.parse("2026-09-04T09:53:19Z"));
        RstExercise newer = exercise(
                "EX-NEW", "GBS CHINA", "175344", "367", Instant.parse("2026-09-09T02:14:55Z"));
        RstExercise otherCenter = exercise(
                "EX-LB", "GBS LEBANON", "175344", "367", Instant.parse("2026-09-01T00:00:00Z"));

        List<RstExercise> latest = BenchmarkingExercises.latestApprovedPerScope(
                List.of(older, newer, otherCenter));

        assertThat(latest).extracting(RstExercise::getExerciseCode).containsExactlyInAnyOrder("EX-NEW", "EX-LB");
    }

    @Test
    void dropsRowsWithoutValidatedAtOrCompleteKey() {
        RstExercise approved = exercise(
                "EX-OK", "GBS CHINA", "175344", "367", Instant.parse("2026-09-09T02:14:55Z"));
        RstExercise unvalidated = exercise("EX-OPEN", "GBS CHINA", "175344", "367", null);
        RstExercise missingPl3 = exercise(
                "EX-NO-PL3", "GBS CHINA", "175344", "", Instant.parse("2026-09-09T02:14:55Z"));

        assertThat(BenchmarkingExercises.latestApprovedPerScope(List.of(approved, unvalidated, missingPl3)))
                .extracting(RstExercise::getExerciseCode)
                .containsExactly("EX-OK");
    }

    private static RstExercise exercise(
            String code, String center, String supervisor, String pl3Code, Instant validatedAt) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        RstExercise exercise = RstExercise.create(
                UUID.randomUUID(),
                code,
                UUID.randomUUID(),
                "SUP1",
                LocalDate.of(2026, 6, 1),
                null,
                null,
                null,
                null,
                now);
        exercise.freezeToolkitSnapshot(
                exercise.getToolkitId(),
                1L,
                UUID.randomUUID(),
                "Toolkit",
                supervisor,
                center,
                "CUSTOMER CARE",
                "BOOKING",
                "BOOKING",
                pl3Code,
                "BOOKING AMENDMENTS",
                false,
                "SUP1",
                now);
        if (validatedAt != null) {
            exercise.markApproved("LTH1", validatedAt);
        }
        return exercise;
    }
}
