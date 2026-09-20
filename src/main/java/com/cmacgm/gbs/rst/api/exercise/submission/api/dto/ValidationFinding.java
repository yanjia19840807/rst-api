package com.cmacgm.gbs.rst.api.exercise.submission.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.cmacgm.gbs.rst.api.exercise.submission.domain.ValidationResult;
import com.cmacgm.gbs.rst.api.exercise.submission.domain.ValidationRule;
import com.cmacgm.gbs.rst.api.exercise.submission.domain.ValidationSeverity;

/**
 * Submit-time validation finding view.
 *
 * <p>{@code severity} is the outcome: {@code OK} passed, otherwise the rule's failure grade.
 */
public record ValidationFinding(
        ValidationRule ruleCode,
        ValidationSeverity severity,
        Detail detail) {

    /**
     * Maps a persisted submit-time finding.
     *
     * @param result stored row
     * @return review/preview view
     */
    public static ValidationFinding from(ValidationResult result) {
        ValidationResult.Detail detail = result.getDetail();
        return new ValidationFinding(
                result.getRuleCode(),
                result.getSeverity(),
                detail == null
                        ? null
                        : new Detail(
                                detail.reason(),
                                detail.comparedMonths(),
                                detail.mismatches().stream()
                                        .map(m -> new MonthMismatch(m.month(), m.daily(), m.monthly()))
                                        .toList(),
                                detail.ratio(),
                                detail.tmsVolumeSum(),
                                detail.dailyVolumeSum(),
                                detail.missingDateCount(),
                                detail.threshold()));
    }

    /**
     * Structured rule payload shown in Submit preview.
     */
    public record Detail(
            String reason,
            int comparedMonths,
            List<MonthMismatch> mismatches,
            BigDecimal ratio,
            BigDecimal tmsVolumeSum,
            BigDecimal dailyVolumeSum,
            Integer missingDateCount,
            BigDecimal threshold) {

        /**
         * Daily vs monthly payload.
         *
         * @param reason skip or comparison outcome
         * @param comparedMonths overlapping months
         * @param mismatches failing months
         * @return detail
         */
        public static Detail dailyVsMonthly(
                String reason, int comparedMonths, List<MonthMismatch> mismatches) {
            return new Detail(reason, comparedMonths, mismatches, null, null, null, null, null);
        }

        /**
         * TMS ratio payload.
         *
         * @param reason comparison outcome
         * @param ratio TMS / Daily when computable
         * @param tmsVolumeSum included TMS volume
         * @param dailyVolumeSum Daily volume in the TMS period
         * @param missingDateCount dates without Daily actuals
         * @param threshold warning threshold
         * @return detail
         */
        public static Detail tmsRatio(
                String reason,
                BigDecimal ratio,
                BigDecimal tmsVolumeSum,
                BigDecimal dailyVolumeSum,
                Integer missingDateCount,
                BigDecimal threshold) {
            return new Detail(
                    reason, 0, List.of(), ratio, tmsVolumeSum, dailyVolumeSum, missingDateCount, threshold);
        }
    }

    /**
     * One month whose daily sum disagrees with the monthly actual.
     */
    public record MonthMismatch(String month, String daily, String monthly) {
    }
}
