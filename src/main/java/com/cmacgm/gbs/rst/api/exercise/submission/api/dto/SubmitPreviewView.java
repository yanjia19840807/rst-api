package com.cmacgm.gbs.rst.api.exercise.submission.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Submit preview response.
 */
public record SubmitPreviewView(
        UUID scenarioId,
        List<ValidationFinding> findings,
        boolean remarksRequired,
        boolean submitBlocked) {
}
