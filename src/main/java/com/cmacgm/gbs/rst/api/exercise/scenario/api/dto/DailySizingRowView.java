package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily simulation row.
 */
public record DailySizingRowView(
        UUID id,
        LocalDate resultDate,
        BigDecimal forecastVolume,
        BigDecimal manualVolume,
        boolean holiday,
        boolean workingDay,
        BigDecimal simulationHc,
        BigDecimal standardCapacity,
        BigDecimal overtimeCapacity,
        BigDecimal backlogStart,
        BigDecimal backlogEnd) {
}
