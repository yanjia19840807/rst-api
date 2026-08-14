package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Forecast point in API responses.
 */
public record ForecastPointView(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal forecastMean,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        BigDecimal acceptedValue) {
}
