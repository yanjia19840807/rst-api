package com.cmacgm.gbs.rst.api.exercise.submission.domain;

/**
 * Closed set of submit-time validation rules. Failure severity is part of the rule.
 */
public enum ValidationRule {
    DAILY_VS_MONTHLY(ValidationSeverity.WARNING);

    private final ValidationSeverity severity;

    ValidationRule(ValidationSeverity severity) {
        this.severity = severity;
    }

    /**
     * Severity when this rule fails.
     *
     * @return OK / WARNING / SEVERE
     */
    public ValidationSeverity severity() {
        return severity;
    }

    /**
     * Whether a failed finding of this rule requires remarks.
     *
     * @return true for WARNING rules
     */
    public boolean requiresRemarksWhenFailed() {
        return severity.requiresRemarksWhenFailed();
    }

    /**
     * Whether a failed finding of this rule blocks Submit.
     *
     * @return true for SEVERE rules
     */
    public boolean blocksSubmitWhenFailed() {
        return severity.blocksSubmitWhenFailed();
    }
}
