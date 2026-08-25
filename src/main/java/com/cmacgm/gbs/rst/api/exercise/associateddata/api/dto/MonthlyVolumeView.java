package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Monthly volume response.
 */
public record MonthlyVolumeView(
        UUID id,
        String month,
        BigDecimal actualVolume,
        BigDecimal commercialRatio,
        String sourceType,
        UUID importBatchId) {
}
