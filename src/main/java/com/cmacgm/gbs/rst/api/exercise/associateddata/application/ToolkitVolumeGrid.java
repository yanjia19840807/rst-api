package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService.VolumeSeed;

/**
 * Builds the Exercise Monthly / Daily overlay from Toolkit stock in the 36-month window.
 */
public final class ToolkitVolumeGrid {

    private ToolkitVolumeGrid() {
    }

    /**
     * Windowed Toolkit months, then a continuous block from the earliest to latest seed key.
     * Holes inside that block are empty rows so the Exercise grid stays consecutive.
     */
    public static List<MonthlyVolumeRequest> monthlyOverlay(
            Map<LocalDate, VolumeSeed> seed, LocalDate sizingMonth) {
        YearMonth min = null;
        YearMonth max = null;
        for (LocalDate month : seed.keySet()) {
            YearMonth ym = YearMonth.from(month);
            if (!VolumeTrainWindows.monthlyInHistoryWindow(ym, sizingMonth)) {
                continue;
            }
            if (min == null || ym.isBefore(min)) {
                min = ym;
            }
            if (max == null || ym.isAfter(max)) {
                max = ym;
            }
        }
        List<MonthlyVolumeRequest> rows = new ArrayList<>();
        if (min == null || max == null) {
            return rows;
        }
        for (YearMonth ym = min; !ym.isAfter(max); ym = ym.plusMonths(1)) {
            String key = ym.toString();
            VolumeSeed point = seed.get(MonthKeys.monthStart(ym));
            BigDecimal actual = point == null ? null : point.actualVolume();
            rows.add(new MonthlyVolumeRequest(key, actual, point == null ? null : point.ratio()));
        }
        return rows;
    }

    /**
     * Windowed Toolkit dates, then a continuous block from the earliest to latest seed key.
     */
    public static List<DailyVolumeRequest> dailyOverlay(
            Map<LocalDate, VolumeSeed> seed, LocalDate sizingMonth) {
        LocalDate min = null;
        LocalDate max = null;
        for (LocalDate date : seed.keySet()) {
            if (!VolumeTrainWindows.dailyInHistoryWindow(date, sizingMonth)) {
                continue;
            }
            if (min == null || date.isBefore(min)) {
                min = date;
            }
            if (max == null || date.isAfter(max)) {
                max = date;
            }
        }
        List<DailyVolumeRequest> rows = new ArrayList<>();
        if (min == null || max == null) {
            return rows;
        }
        for (LocalDate date = min; !date.isAfter(max); date = date.plusDays(1)) {
            VolumeSeed point = seed.get(date);
            rows.add(new DailyVolumeRequest(
                    date,
                    point == null ? null : point.actualVolume(),
                    point == null ? null : point.ratio()));
        }
        return rows;
    }
}
