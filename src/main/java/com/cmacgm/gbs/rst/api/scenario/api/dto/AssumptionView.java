package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Assumption response.
 */
public record AssumptionView(
        UUID id,
        String parameterCode,
        BigDecimal numericValue,
        String textValue,
        Boolean booleanValue,
        String unit) {
}
