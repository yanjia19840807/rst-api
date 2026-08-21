package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Canonical Toolkit actuals for Volume add/import pre-fill.
 */
public record ToolkitVolumePointsView(
        List<ToolkitMonthlyPointView> monthly,
        List<ToolkitDailyPointView> daily) {

    /**
     * One canonical monthly actual.
     */
    public record ToolkitMonthlyPointView(String month, BigDecimal actualVolume) {
    }

    /**
     * One canonical daily actual.
     */
    public record ToolkitDailyPointView(LocalDate volumeDate, BigDecimal actualVolume) {
    }
}
