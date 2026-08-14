package com.cmacgm.gbs.rst.api.scenario.api.dto;

/**
 * Optional committed preview snapshot. Omit (null) to clear prior results.
 * Slot may be null when only sizing was run.
 */
public record CommitResults(
        ForecastBundleView forecast,
        MonthlySizingView monthly,
        DailySizingView daily,
        SlotSimulationView slot) {
}
