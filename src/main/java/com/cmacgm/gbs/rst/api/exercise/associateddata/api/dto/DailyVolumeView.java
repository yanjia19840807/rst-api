package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily volume response.
 */
public record DailyVolumeView(
        UUID id,
        LocalDate volumeDate,
        BigDecimal actualVolume,
        BigDecimal dailyAdjustmentRatio,
        String sourceType,
        UUID importBatchId) {
}
