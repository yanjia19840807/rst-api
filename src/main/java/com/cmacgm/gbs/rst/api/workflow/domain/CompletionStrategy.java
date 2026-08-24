package com.cmacgm.gbs.rst.api.workflow.domain;

/**
 * How a node completes when it has more than one actor.
 */
public enum CompletionStrategy {
    /** Any one pending actor completing the node is enough (approver or delegate). */
    OR,
    /** Every actor must approve before the node completes. */
    AND
}
