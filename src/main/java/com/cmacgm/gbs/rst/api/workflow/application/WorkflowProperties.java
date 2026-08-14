package com.cmacgm.gbs.rst.api.workflow.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Workflow routing settings. Timesheet has no LTH column yet, so LTH steps share one
 * position id until IAM provides per-org LTH positions.
 *
 * @param lthPositionId position id assigned to every LTH step
 */
@ConfigurationProperties(prefix = "rst.workflow")
public record WorkflowProperties(String lthPositionId) {

    /**
     * Canonicalizes a blank LTH position to the development default.
     *
     * @param lthPositionId configured value
     */
    public WorkflowProperties {
        if (lthPositionId == null || lthPositionId.isBlank()) {
            lthPositionId = "LTH";
        }
    }
}
