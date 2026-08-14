package com.cmacgm.gbs.rst.api.approval.api.dto;

import java.util.List;

/**
 * Approver queue response: filtered rows, filter options, and awaiting metrics.
 */
public record ApprovalQueueView(
        List<ApprovalQueueItem> items,
        QueueMetrics metrics,
        List<String> toolkitNames,
        List<String> pl3Names) {
}
