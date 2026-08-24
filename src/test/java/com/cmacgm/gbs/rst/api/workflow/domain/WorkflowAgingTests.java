package com.cmacgm.gbs.rst.api.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkflowAgingTests {

    @Test
    void firstStepUsesSubmittedAt() {
        Instant submitted = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(WorkflowAging.currentStepStartedAt((ProcessInstance) null, submitted)).isEqualTo(submitted);
    }

    @Test
    void laterStepUsesLastApprove() {
        Instant submitted = Instant.parse("2026-01-01T00:00:00Z");
        Instant managerApproved = Instant.parse("2026-01-10T00:00:00Z");
        Instant cdhApproved = Instant.parse("2026-01-12T00:00:00Z");
        ProcessInstance instance = ProcessInstance.start(
                UUID.randomUUID(), null, "s1", UUID.randomUUID(), submitted);
        instance.openReview(TaskNode.MANAGER, List.of(new ProcessInstance.Assignee("P1", "m1")), submitted);
        TaskActor manager = instance.findCurrentPendingTask().orElseThrow().findAnyPendingActor().orElseThrow();
        instance.approve(manager, null, UUID.randomUUID(), managerApproved);
        instance.openReview(TaskNode.CDH, List.of(new ProcessInstance.Assignee("P2", "c1")), managerApproved);
        TaskActor cdh = instance.findCurrentPendingTask().orElseThrow().findAnyPendingActor().orElseThrow();
        instance.approve(cdh, null, UUID.randomUUID(), cdhApproved);
        assertThat(WorkflowAging.currentStepStartedAt(instance, submitted)).isEqualTo(cdhApproved);
    }

    @Test
    void daysBetweenUsesUtcCalendarDates() {
        Instant from = Instant.parse("2026-01-01T23:00:00Z");
        Instant sameUtcDay = Instant.parse("2026-01-01T23:59:59Z");
        Instant nextUtcDay = Instant.parse("2026-01-02T00:00:00Z");
        assertThat(WorkflowAging.daysBetween(from, sameUtcDay)).isZero();
        assertThat(WorkflowAging.daysBetween(from, nextUtcDay)).isEqualTo(1);
        assertThat(WorkflowAging.daysBetween(null, nextUtcDay)).isZero();
    }
}
