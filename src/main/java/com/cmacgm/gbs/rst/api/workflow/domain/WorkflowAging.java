package com.cmacgm.gbs.rst.api.workflow.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Shared aging clock: days waiting on the current review step, not since original submit.
 */
public final class WorkflowAging {

    private WorkflowAging() {
    }

    /**
     * Instant the current READY step started waiting: last APPROVE, otherwise submit.
     *
     * @param workflow current workflow; null treated as no actions
     * @param submittedAt submission time; used on the first step
     * @return aging start instant
     */
    public static Instant currentStepStartedAt(WorkflowInstance workflow, Instant submittedAt) {
        return currentStepStartedAt(
                workflow == null ? List.of() : workflow.getActions(),
                submittedAt);
    }

    /**
     * Instant the current READY step started waiting: last APPROVE, otherwise submit.
     *
     * @param actions workflow actions
     * @param submittedAt submission time; used on the first step
     * @return aging start instant
     */
    public static Instant currentStepStartedAt(List<WorkflowAction> actions, Instant submittedAt) {
        WorkflowAction previous = lastApprove(actions);
        if (previous != null && previous.getActionAt() != null) {
            return previous.getActionAt();
        }
        return submittedAt;
    }

    /**
     * Latest APPROVE action, if any.
     *
     * @param workflow current workflow
     * @return last approve, or null
     */
    public static WorkflowAction lastApprove(WorkflowInstance workflow) {
        return lastApprove(workflow == null ? List.of() : workflow.getActions());
    }

    /**
     * Latest APPROVE action from a list, if any.
     *
     * @param actions workflow actions
     * @return last approve, or null
     */
    public static WorkflowAction lastApprove(List<WorkflowAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        return actions.stream()
                .filter(action -> "APPROVE".equals(action.getActionType()))
                .max(Comparator
                        .comparing(WorkflowAction::getActionAt)
                        .thenComparingInt(WorkflowAction::getActionSeq))
                .orElse(null);
    }

    /**
     * Whole UTC calendar days between two instants, never negative.
     *
     * @param from start instant
     * @param to end instant
     * @return elapsed days, or 0 when either instant is missing
     */
    public static int daysBetween(Instant from, Instant to) {
        if (from == null || to == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(
                from.atZone(ZoneOffset.UTC).toLocalDate(),
                to.atZone(ZoneOffset.UTC).toLocalDate());
        return (int) Math.max(0, days);
    }
}
