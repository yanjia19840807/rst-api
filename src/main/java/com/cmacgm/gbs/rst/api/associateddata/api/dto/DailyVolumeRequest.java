package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Daily volume request row.
 */
public record DailyVolumeRequest(@NotNull LocalDate volumeDate, BigDecimal actualVolume) {
}
