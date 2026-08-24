package com.cmacgm.gbs.rst.api.workflow.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * Shared aging clock: days waiting on the current review node, not since original submit.
 */
public final class WorkflowAging {

    private WorkflowAging() {
    }

    /**
     * Instant the current review node started waiting: last approver APPROVED, otherwise submit.
     *
     * @param instance current process; null treated as no history
     * @param submittedAt submit time; used on the first hop
     * @return aging start instant
     */
    public static Instant currentStepStartedAt(ProcessInstance instance, Instant submittedAt) {
        TaskActor previous = lastApprove(instance);
        if (previous != null && previous.getActedAt() != null) {
            return previous.getActedAt();
        }
        return submittedAt;
    }

    /**
     * Latest approver APPROVED actor, if any.
     *
     * @param instance current process
     * @return last approve, or null
     */
    public static TaskActor lastApprove(ProcessInstance instance) {
        if (instance == null) {
            return null;
        }
        return instance.getTasks().stream()
                .flatMap(task -> task.getActors().stream())
                .filter(actor -> actor.getStatus() == ActorStatus.APPROVED
                        && actor.getActorType() != ActorType.INITIATOR)
                .max(Comparator.comparing(TaskActor::getActedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    /**
     * Latest Return actor, if any.
     *
     * @param instance current process
     * @return last reject, or null
     */
    public static TaskActor lastReturn(ProcessInstance instance) {
        if (instance == null) {
            return null;
        }
        return instance.getTasks().stream()
                .flatMap(task -> task.getActors().stream())
                .filter(actor -> actor.getStatus() == ActorStatus.RETURNED)
                .max(Comparator.comparing(TaskActor::getActedAt, Comparator.nullsLast(Comparator.naturalOrder())))
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
