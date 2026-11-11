package com.cmacgm.gbs.rst.api.workflow.approval.api.dto;

/**
 * Unfiltered awaiting-tab metrics.
 * {@code overdue} is 2 to 4 days on the current step.
 * {@code dueWithin2Days} is 5 days or more.
 * The two counts do not overlap. {@code highRisk} matches the 5-day count.
 */
public record QueueMetrics(int awaitingMe, int overdue, int dueWithin2Days, int highRisk) {
}
