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
    void keepsLatestSizingMonthPerCenterSupervisorPl3() {
        RstExercise olderMonth = exercise(
                "EX-JUN",
                "GBS CHINA",
                "175344",
                "367",
                LocalDate.of(2026, 6, 1),
                Instant.parse("2026-09-20T02:00:00Z"));
        RstExercise newerMonth = exercise(
                "EX-SEP",
                "GBS CHINA",
                "175344",
                "367",
                LocalDate.of(2026, 9, 1),
                Instant.parse("2026-09-05T02:00:00Z"));
        RstExercise otherCenter = exercise(
                "EX-LB",
                "GBS LEBANON",
                "175344",
                "367",
                LocalDate.of(2026, 6, 1),
                Instant.parse("2026-09-01T00:00:00Z"));

        List<RstExercise> latest = BenchmarkingExercises.latestApprovedPerScope(
                List.of(olderMonth, newerMonth, otherCenter));

        assertThat(latest).extracting(RstExercise::getExerciseCode).containsExactlyInAnyOrder("EX-SEP", "EX-LB");
    }

    @Test
    void sameSizingMonthFallsBackToLaterValidatedAt() {
        RstExercise older = exercise(
                "EX-OLD", "GBS CHINA", "175344", "367", Instant.parse("2026-09-04T09:53:19Z"));
        RstExercise newer = exercise(
                "EX-NEW", "GBS CHINA", "175344", "367", Instant.parse("2026-09-09T02:14:55Z"));

        assertThat(BenchmarkingExercises.latestApprovedPerScope(List.of(older, newer)))
                .extracting(RstExercise::getExerciseCode)
                .containsExactly("EX-NEW");
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

    @Test
    void picksLatestAmongExercisesThatMatchFilter() {
        RstExercise older = exercise(
                "EX-OLD", "GBS CHINA", "175344", "367", Instant.parse("2026-06-10T02:00:00Z"));
        RstExercise newer = exercise(
                "EX-NEW", "GBS CHINA", "175344", "367", Instant.parse("2026-09-10T02:00:00Z"));

        List<RstExercise> latest = BenchmarkingExercises.latestApprovedPerScope(
                List.of(older, newer),
                exercise -> "EX-OLD".equals(exercise.getExerciseCode()));

        assertThat(latest).extracting(RstExercise::getExerciseCode).containsExactly("EX-OLD");
    }

    @Test
    void keepsLatestPerSupervisorOnTheSamePl3() {
        RstExercise supervisorA = exercise(
                "EX-A", "GBS CHINA", "175344", "367", Instant.parse("2026-06-10T02:00:00Z"));
        RstExercise supervisorB = exercise(
                "EX-B", "GBS CHINA", "188001", "367", Instant.parse("2026-03-10T02:00:00Z"));

        assertThat(BenchmarkingExercises.latestApprovedPerScope(List.of(supervisorA, supervisorB)))
                .extracting(RstExercise::getExerciseCode)
                .containsExactlyInAnyOrder("EX-A", "EX-B");
    }

    @Test
    void keepsFirstWhenValidatedAtIsEqual() {
        Instant sameTime = Instant.parse("2026-09-09T02:14:55Z");
        RstExercise first = exercise("EX-A", "GBS CHINA", "175344", "367", sameTime);
        RstExercise laterCode = exercise("EX-Z", "GBS CHINA", "175344", "367", sameTime);

        assertThat(BenchmarkingExercises.latestApprovedPerScope(List.of(first, laterCode)))
                .extracting(RstExercise::getExerciseCode)
                .containsExactly("EX-A");
    }

    private static RstExercise exercise(
            String code, String center, String supervisor, String pl3Code, Instant validatedAt) {
        return exercise(code, center, supervisor, pl3Code, LocalDate.of(2026, 6, 1), validatedAt);
    }

    private static RstExercise exercise(
            String code,
            String center,
            String supervisor,
            String pl3Code,
            LocalDate sizingMonth,
            Instant validatedAt) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        RstExercise exercise = RstExercise.create(
                UUID.randomUUID(),
                code,
                UUID.randomUUID(),
                "SUP1",
                sizingMonth,
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
