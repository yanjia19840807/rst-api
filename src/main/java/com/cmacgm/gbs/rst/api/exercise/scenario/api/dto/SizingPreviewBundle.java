package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

/**
 * Forecast + sizing preview payload (not persisted).
 */
public record SizingPreviewBundle(
        ForecastBundleView forecast, MonthlySizingView monthly, DailySizingView daily) {
}
