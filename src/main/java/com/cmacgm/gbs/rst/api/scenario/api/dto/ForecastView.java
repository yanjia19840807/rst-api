package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Latest ACCEPTED forecast response.
 */
public record ForecastView(
        UUID id,
        int runNo,
        String method,
        String methodVersion,
        String status,
        String forecastLevel,
        LocalDate trainingFrom,
        LocalDate trainingTo,
        String featureMetadata,
        Instant startedAt,
        Instant completedAt,
        List<ForecastPointView> points) {
}
