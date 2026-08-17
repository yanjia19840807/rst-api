package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.util.List;

/**
 * Validation Workflow: one page of stuck-exercise rows and unfiltered dropdown options.
 */
public record ValidationWorkflowView(
        List<ValidationWorkflowRow> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<String> centers,
        List<String> domains,
        List<String> pl3Names,
        List<String> toolkitNames) {
}
