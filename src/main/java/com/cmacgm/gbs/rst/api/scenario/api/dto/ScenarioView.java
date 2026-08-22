package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
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
        BigDecimal rightSizingHc,
        long version,
        List<ShiftView> shifts) {
}
