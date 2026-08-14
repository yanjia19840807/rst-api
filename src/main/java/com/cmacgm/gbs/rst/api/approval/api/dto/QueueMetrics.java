package com.cmacgm.gbs.rst.api.approval.api.dto;

/**
 * Unfiltered awaiting-tab metrics.
 */
public record QueueMetrics(int awaitingMe, int overdue, int dueWithin2Days, int highRisk) {
}
