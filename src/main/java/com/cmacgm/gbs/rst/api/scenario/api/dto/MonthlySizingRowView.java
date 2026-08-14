package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Monthly sizing row.
 */
public record MonthlySizingRowView(
        UUID id,
        String month,
        BigDecimal forecastVolume,
        BigDecimal manualVolume,
        BigDecimal workdays,
        BigDecimal weekendDays,
        BigDecimal cycleTimeSeconds,
        BigDecimal nominalHcWithoutOt,
        BigDecimal nominalHcWithOt,
        BigDecimal productionSupportFte,
        BigDecimal rightSizingHc,
        BigDecimal capacityCreation) {
}
