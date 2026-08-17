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
        assertThat(WorkflowAging.currentStepStartedAt(List.of(), submitted)).isEqualTo(submitted);
        assertThat(WorkflowAging.currentStepStartedAt((WorkflowInstance) null, submitted)).isEqualTo(submitted);
    }

    @Test
    void laterStepUsesLastApprove() {
        Instant submitted = Instant.parse("2026-01-01T00:00:00Z");
        Instant managerApproved = Instant.parse("2026-01-10T00:00:00Z");
        Instant cdhApproved = Instant.parse("2026-01-12T00:00:00Z");
        List<WorkflowAction> actions = List.of(
                WorkflowAction.approve((short) 1, "m1", "MANAGER", null, UUID.randomUUID(), managerApproved),
                WorkflowAction.approve((short) 2, "c1", "CDH", null, UUID.randomUUID(), cdhApproved));
        assertThat(WorkflowAging.currentStepStartedAt(actions, submitted)).isEqualTo(cdhApproved);
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
