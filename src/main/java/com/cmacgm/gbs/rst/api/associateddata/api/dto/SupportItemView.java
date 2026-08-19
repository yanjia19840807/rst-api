package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Support item response. Category name is a snapshot from the lookup at write time.
 */
public record SupportItemView(
        UUID id,
        UUID lineageId,
        UUID categoryId,
        String category,
        String activity,
        String frequencyCode,
        BigDecimal volume,
        String unitOfMeasure,
        BigDecimal workloadPerUnitMinutes,
        BigDecimal annualMultiplier,
        BigDecimal workloadPerYearHours,
        BigDecimal supportFte,
        String comments,
        String calculationVersion) {
}
