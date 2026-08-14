package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Support item response.
 */
public record SupportItemView(
        UUID id, UUID lineageId, String category, String activity, String frequencyCode,
        BigDecimal volume, String unitOfMeasure, BigDecimal workloadPerUnitMinutes,
        BigDecimal annualMultiplier, BigDecimal workloadPerYearHours, BigDecimal supportFte,
        String comments, String calculationVersion) {
}
