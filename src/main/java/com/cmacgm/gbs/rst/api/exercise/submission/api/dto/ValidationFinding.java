package com.cmacgm.gbs.rst.api.exercise.submission.api.dto;

import java.util.List;

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
     * Structured rule payload shown in Submit preview.
     */
    public record Detail(String reason, int comparedMonths, List<MonthMismatch> mismatches) {
    }

    /**
     * One month whose daily sum disagrees with the monthly actual.
     */
    public record MonthMismatch(String month, String daily, String monthly) {
    }
}
