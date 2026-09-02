package com.cmacgm.gbs.rst.api.exercise.submission.api.dto;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.api.dto.TimesheetAlignmentView;

/**
 * Submit preview response.
 */
public record SubmitPreviewView(
        UUID scenarioId,
        List<ValidationFinding> findings,
        boolean remarksRequired,
        boolean submitBlocked,
        TimesheetAlignmentView timesheetAlignment,
        boolean scopeAcknowledgementRequired) {
}
