package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/**
 * Monthly volume request row.
 */
public record MonthlyVolumeRequest(@NotBlank String month, BigDecimal actualVolume) {
}
