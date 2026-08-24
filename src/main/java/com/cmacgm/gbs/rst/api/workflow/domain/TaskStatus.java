package com.cmacgm.gbs.rst.api.workflow.domain;

/**
 * Outcome of one visit to a node. Pending is a todo; the rest are done.
 */
public enum TaskStatus {
    PENDING,
    APPROVED,
    RETURNED,
    REJECTED,
    WITHDRAWN;

    /**
     * Whether the node is still waiting for a decision.
     *
     * @return true when pending
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Whether Supervisor may edit and submit again after this outcome.
     *
     * @return true for return / withdraw
     */
    public boolean allowsResubmit() {
        return this == RETURNED || this == WITHDRAWN;
    }
}
