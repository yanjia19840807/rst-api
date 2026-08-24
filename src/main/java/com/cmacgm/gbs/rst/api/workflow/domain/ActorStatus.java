package com.cmacgm.gbs.rst.api.workflow.domain;

/**
 * Personal outcome on one task. Pending rows are todos; the rest are done / cancelled.
 */
public enum ActorStatus {
    PENDING,
    APPROVED,
    RETURNED,
    REJECTED,
    WITHDRAWN,
    CANCELLED;

    /**
     * Whether this actor may still decide.
     *
     * @return true when pending
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Whether this outcome appears in ActionView / history.
     *
     * @return true when the actor has a public decision
     */
    public boolean isHistory() {
        return this == APPROVED || this == RETURNED || this == REJECTED || this == WITHDRAWN;
    }
}
