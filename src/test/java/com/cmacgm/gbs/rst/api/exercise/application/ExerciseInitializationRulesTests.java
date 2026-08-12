package com.cmacgm.gbs.rst.api.exercise.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.application.VolumeTrainWindows;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.junit.jupiter.api.Test;

class ExerciseInitializationRulesTests {

    @Test
    void derivesAllVolumeTrainingWindows() {
        LocalDate sizingMonth = LocalDate.of(2026, 1, 1);

        assertEquals(
                java.util.List.of(
                        LocalDate.of(2025, 11, 1),
                        LocalDate.of(2025, 12, 1),
                        LocalDate.of(2026, 1, 1)),
                VolumeTrainWindows.monthlyTrainMonths(sizingMonth));
        assertEquals(31, VolumeTrainWindows.dailyTrainDates(sizingMonth).size());
        assertEquals(
                2 * 7 * 26,
                VolumeTrainWindows.slotTrainBounds(LocalDate.of(2025, 12, 29), (short) 2)
                        .size());
    }

    @Test
    void resolvesSizingTmsAndSlotHolidayYears() {
        UUID id = UUID.randomUUID();
        RstExercise exercise = RstExercise.create(
                id,
                "EX-TEST",
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 28),
                (short) 2,
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2026, 1, 31),
                Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(
                Set.of((short) 2025, (short) 2026, (short) 2027),
                ExerciseInitializationService.resolveHolidayYears(exercise));
    }

    @Test
    void kpiBusinessKeyTreatsNullValuesDeterministically() {
        assertEquals(
                new KpiBusinessKey("", "Singapore", ""),
                new KpiBusinessKey(null, "Singapore", null));
    }
}
