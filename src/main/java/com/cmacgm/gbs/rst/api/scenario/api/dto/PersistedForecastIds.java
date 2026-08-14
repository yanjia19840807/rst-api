package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.util.UUID;

/**
 * Ids of persisted monthly/daily forecast runs after commit.
 */
public record PersistedForecastIds(UUID monthlyForecastRunId, UUID dailyForecastRunId) {
}
