package com.cmacgm.gbs.rst.api.workflow.domain;

/**
 * Fixed RST review chain. Order is code, not a process-definition table.
 */
public enum TaskNode {
    SUBMIT((short) 0, false),
    SR_MANAGER((short) 1, true),
    DOMAIN_HEAD((short) 2, true),
    LOCAL_TRANSFORMATION_HEAD((short) 3, true);

    private final short order;
    private final boolean review;

    TaskNode(short order, boolean review) {
        this.order = order;
        this.review = review;
    }

    /**
     * Display / API step number.
     *
     * @return 0 for submit, 1–3 for review hops
     */
    public short order() {
        return order;
    }

    /**
     * Whether this node waits on an approver.
     *
     * @return true for Sr Manager / Domain Head / Local Transformation Head
     */
    public boolean isReview() {
        return review;
    }

    /**
     * Next review hop, or empty after Local Transformation Head.
     *
     * @return next node, or null when this is the last review hop
     */
    public TaskNode nextReview() {
        return switch (this) {
            case SR_MANAGER -> DOMAIN_HEAD;
            case DOMAIN_HEAD -> LOCAL_TRANSFORMATION_HEAD;
            default -> null;
        };
    }

    /**
     * Role code used by Timesheet routing and the API.
     *
     * @return SR_MANAGER / DOMAIN_HEAD / LOCAL_TRANSFORMATION_HEAD, or SUPERVISOR for submit
     */
    public String roleCode() {
        return this == SUBMIT ? "SUPERVISOR" : name();
    }

    /**
     * Resolves a review node from its step number.
     *
     * @param step 1–3
     * @return node, or null when unknown
     */
    public static TaskNode reviewOf(Short step) {
        if (step == null) {
            return null;
        }
        return switch (step) {
            case 1 -> SR_MANAGER;
            case 2 -> DOMAIN_HEAD;
            case 3 -> LOCAL_TRANSFORMATION_HEAD;
            default -> null;
        };
    }
}
