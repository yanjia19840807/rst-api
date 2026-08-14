package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scenario response.
 */
public record ScenarioView(
        UUID id,
        String scenarioCode,
        String name,
        String description,
        String status,
        Instant officialAt,
        long version,
        List<AssumptionView> assumptions,
        List<ShiftView> shifts) {
}
