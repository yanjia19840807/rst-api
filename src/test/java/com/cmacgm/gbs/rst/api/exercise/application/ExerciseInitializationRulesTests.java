package com.cmacgm.gbs.rst.api.exercise.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import com.cmacgm.gbs.rst.api.exercise.associateddata.application.VolumeTrainWindows;
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
    void kpiBusinessKeyTreatsNullValuesDeterministically() {
        assertEquals(
                new KpiBusinessKey("", "Singapore", ""),
                new KpiBusinessKey(null, "Singapore", null));
    }
}
