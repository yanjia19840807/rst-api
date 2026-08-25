package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Frozen training actuals for the official Scenario forecast runs.
 */
public record ForecastTrainingBundleView(
        List<ForecastTrainingObservationView> monthly,
        List<ForecastTrainingObservationView> daily) {

    /**
     * One frozen training actual.
     */
    public record ForecastTrainingObservationView(
            String grain,
            LocalDate periodStart,
            BigDecimal actualVolume,
            String source,
            UUID sourceExerciseId) {
    }
}
