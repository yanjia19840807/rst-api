package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Frozen Shared KPI view.
 */
public record ExerciseKpiView(
        UUID id, UUID sourceSelectionId, String carrier, String site,
        String customerCountry, BigDecimal deliveryHc, boolean valid) {
}
