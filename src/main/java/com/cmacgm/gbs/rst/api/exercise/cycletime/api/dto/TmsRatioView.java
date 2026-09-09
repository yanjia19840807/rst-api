package com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto;

import java.math.BigDecimal;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.TmsRatioMath;

/**
 * SYSTEM TMS ratio shown on Exercise TMS Metrics and the Embedded TMS editor.
 */
public record TmsRatioView(
        String reason,
        BigDecimal ratio,
        BigDecimal tmsVolumeSum,
        BigDecimal dailyVolumeSum,
        int missingDateCount,
        BigDecimal threshold) {

    /**
     * Maps a computed ratio result.
     *
     * @param result domain result
     * @return API view
     */
    public static TmsRatioView from(TmsRatioMath.Result result) {
        return new TmsRatioView(
                result.reason(),
                result.ratio(),
                result.tmsVolumeSum(),
                result.dailyVolumeSum(),
                result.missingDateCount(),
                TmsRatioMath.THRESHOLD);
    }
}
