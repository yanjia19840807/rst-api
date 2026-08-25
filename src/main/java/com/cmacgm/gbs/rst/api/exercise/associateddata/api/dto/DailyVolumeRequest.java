package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Daily volume request row.
 */
public record DailyVolumeRequest(
        @NotNull LocalDate volumeDate, BigDecimal actualVolume, BigDecimal dailyAdjustmentRatio) {

    public DailyVolumeRequest(LocalDate volumeDate, BigDecimal actualVolume) {
        this(volumeDate, actualVolume, null);
    }
}
