package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One frozen training actual stored on {@link ForecastRun#getTrainingObservations()}.
 * Grain is the parent run's forecast_level (MONTHLY / DAILY).
 */
public record ForecastTrainingSnapshot(
        LocalDate periodStart,
        BigDecimal actualVolume,
        String source,
        UUID sourceExerciseId) {
}
