package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class VolumeTrainWindowsTests {

    @Test
    void historyWindowIs36MonthsEndingAtSizingMonth() {
        LocalDate sizing = LocalDate.of(2026, 9, 1);
        assertThat(VolumeTrainWindows.monthlyHistoryFloor(sizing)).isEqualTo(YearMonth.of(2023, 10));
        assertThat(VolumeTrainWindows.dailyHistoryFloor(sizing)).isEqualTo(LocalDate.of(2023, 10, 1));
        assertThat(VolumeTrainWindows.monthlyInHistoryWindow(YearMonth.of(2023, 10), sizing)).isTrue();
        assertThat(VolumeTrainWindows.monthlyInHistoryWindow(YearMonth.of(2023, 9), sizing)).isFalse();
        assertThat(VolumeTrainWindows.monthlyInHistoryWindow(YearMonth.of(2026, 9), sizing)).isTrue();
        assertThat(VolumeTrainWindows.monthlyInHistoryWindow(YearMonth.of(2026, 10), sizing)).isFalse();
        assertThat(VolumeTrainWindows.dailyInHistoryWindow(LocalDate.of(2023, 9, 30), sizing)).isFalse();
        assertThat(VolumeTrainWindows.dailyInHistoryWindow(LocalDate.of(2026, 9, 30), sizing)).isTrue();
    }
}
