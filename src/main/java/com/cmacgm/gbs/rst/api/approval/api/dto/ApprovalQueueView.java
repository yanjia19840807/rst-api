package com.cmacgm.gbs.rst.api.approval.api.dto;

import java.util.List;

/**
 * Approver queue response: one page of filtered rows, filter options, and awaiting metrics.
 */
public record ApprovalQueueView(
        List<ApprovalQueueItem> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        QueueMetrics metrics,
        List<String> toolkitNames,
        List<String> pl3Names) {
}
