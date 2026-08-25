package com.cmacgm.gbs.rst.api.exercise.submission.domain;

/**
 * Closed set of submit-time validation severities.
 *
 * <ul>
 *   <li>OK — passed; does not affect Submit
 *   <li>WARNING — Submit is allowed only with remarks
 *   <li>SEVERE — Submit is blocked until the finding is resolved
 * </ul>
 */
public enum ValidationSeverity {
    OK,
    WARNING,
    SEVERE;

    /**
     * Whether a failed finding of this grade requires remarks.
     *
     * @return true for WARNING
     */
    public boolean requiresRemarksWhenFailed() {
        return this == WARNING;
    }

    /**
     * Whether a failed finding of this grade blocks Submit.
     *
     * @return true for SEVERE
     */
    public boolean blocksSubmitWhenFailed() {
        return this == SEVERE;
    }
}
