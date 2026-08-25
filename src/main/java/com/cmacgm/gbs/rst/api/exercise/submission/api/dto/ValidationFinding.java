package com.cmacgm.gbs.rst.api.exercise.submission.api.dto;

/**
 * Validation finding view.
 */
public record ValidationFinding(
        String ruleCode, String severity, boolean passed, String remarks) {
}
