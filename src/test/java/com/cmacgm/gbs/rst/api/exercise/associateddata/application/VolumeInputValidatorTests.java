package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeRequest;

class VolumeInputValidatorTests {

    private final VolumeInputValidator validator = new VolumeInputValidator();

    @Test
    void monthlyImportRejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validateMonthlyImportRows(List.of(), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no monthly rows");
    }

    @Test
    void monthlyImportRejectsBlankActual() {
        assertThatThrownBy(() -> validator.validateMonthlyImportRows(
                        List.of(new MonthlyVolumeRequest("2026-01", null, null)),
                        LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("actualVolume is required");
    }

    @Test
    void monthlyImportRejectsFileGap() {
        assertThatThrownBy(() -> validator.validateMonthlyImportRows(
                        List.of(
                                new MonthlyVolumeRequest("2026-01", BigDecimal.ONE, null),
                                new MonthlyVolumeRequest("2026-03", BigDecimal.ONE, null)),
                        LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Monthly volumes must be continuous");
    }

    @Test
    void monthlyUnionRejectsDisconnectedToolkitIsland() {
        assertThatThrownBy(() -> validator.requireContinuousMonthUnion(
                        List.of(YearMonth.of(2026, 5)),
                        List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("overlap or adjoin existing Toolkit months")
                .hasMessageContaining("2026-03")
                .hasMessageContaining("2026-05");
    }

    @Test
    void monthlyUnionAllowsAdjacentExtension() {
        validator.requireContinuousMonthUnion(
                List.of(YearMonth.of(2026, 4), YearMonth.of(2026, 5)),
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)));
    }

    @Test
    void monthlyUnionAllowsOverlap() {
        validator.requireContinuousMonthUnion(
                List.of(YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)));
    }

    @Test
    void monthlyImportRejectsMonthBefore36MonthWindow() {
        assertThatThrownBy(() -> validator.validateMonthlyImportRows(
                        List.of(new MonthlyVolumeRequest("2023-09", BigDecimal.ONE, null)),
                        LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Earliest allowed month is 2023-10");
    }

    @Test
    void monthlyImportAllowsEarliestMonthInWindow() {
        validator.validateMonthlyImportRows(
                List.of(new MonthlyVolumeRequest("2023-10", BigDecimal.ONE, null)),
                LocalDate.of(2026, 9, 1));
    }

    @Test
    void monthlyUnionAllowsHealingExistingToolkitGap() {
        validator.requireContinuousMonthUnion(
                List.of(YearMonth.of(2026, 2)),
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 3)));
    }

    @Test
    void dailyImportRejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validateDailyImportRows(List.of(), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no daily rows");
    }

    @Test
    void dailyImportRejectsDateBefore36MonthWindow() {
        assertThatThrownBy(() -> validator.validateDailyImportRows(
                        List.of(new DailyVolumeRequest(LocalDate.of(2023, 9, 30), BigDecimal.ONE, null)),
                        LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Earliest allowed date is 2023-10-01");
    }

    @Test
    void dailyUnionRejectsDisconnectedToolkitIsland() {
        assertThatThrownBy(() -> validator.requireContinuousDateUnion(
                        List.of(LocalDate.of(2026, 6, 3)),
                        List.of(LocalDate.of(2026, 6, 1))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("overlap or adjoin existing Toolkit dates");
    }

    @Test
    void planSlotImportInfersOneWeekAndPadsTail() {
        List<SlotVolumeRequest> rows = days(LocalDate.of(2026, 6, 1), 3);
        SlotImportPlan plan = validator.planSlotImport(rows);
        assertThat(plan.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(plan.weeks()).isEqualTo((short) 1);
        assertThat(plan.fileRowCount()).isEqualTo(26 * 3);
        assertThat(plan.paddedCount()).isEqualTo(26 * 4);
        assertThat(plan.totalSlots()).isEqualTo(26 * 7);
    }

    @Test
    void planSlotImportRejectsDateGap() {
        List<SlotVolumeRequest> rows = new ArrayList<>();
        rows.addAll(days(LocalDate.of(2026, 6, 1), 1));
        rows.addAll(days(LocalDate.of(2026, 6, 3), 1));
        assertThatThrownBy(() -> validator.planSlotImport(rows))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Dates must be continuous");
    }

    @Test
    void planSlotImportRejectsMissingIntraDaySlot() {
        List<SlotVolumeRequest> rows = days(LocalDate.of(2026, 6, 1), 1);
        rows.remove(1);
        assertThatThrownBy(() -> validator.planSlotImport(rows))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void planSlotImportRejectsMoreThanTwelveWeeks() {
        List<SlotVolumeRequest> rows = days(LocalDate.of(2026, 6, 1), 12 * 7 + 1);
        assertThatThrownBy(() -> validator.planSlotImport(rows))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot exceed 12 weeks");
    }

    private static List<SlotVolumeRequest> days(LocalDate start, int count) {
        List<SlotVolumeRequest> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (VolumeTrainWindows.SlotBound bound : VolumeTrainWindows.dayBounds(start.plusDays(i))) {
                rows.add(new SlotVolumeRequest(bound.start(), bound.end(), BigDecimal.ONE));
            }
        }
        return rows;
    }
}
