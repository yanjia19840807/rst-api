package com.cmacgm.gbs.rst.api.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.security.Handler;
import org.junit.jupiter.api.Test;

class ProcessInstanceTests {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void submitHandlerRecordsDelegateOnInitiator() {
        Handler handler = new Handler("sup1", "Yang Brenda", "agt1", "Li Wei");
        ProcessInstance instance = ProcessInstance.start(
                UUID.randomUUID(), "go", handler, UUID.randomUUID(), T0);

        assertThat(instance.getSubmittedBy().hasActor()).isTrue();
        assertThat(instance.getSubmittedBy().displayName())
                .isEqualTo("Li Wei (on behalf of Yang Brenda)");
        assertThat(instance.getTasks().get(0).getActors().get(0).handlerDisplayName(java.util.Map.of()))
                .isEqualTo("Li Wei (on behalf of Yang Brenda)");
    }

    @Test
    void startRecordsApprovedSubmitAndOpensManager() {
        ProcessInstance instance = ProcessInstance.start(
                UUID.randomUUID(), "go", "sup1", UUID.randomUUID(), T0);
        instance.openReview(TaskNode.MANAGER, List.of(new ProcessInstance.Assignee("P-M", "mgr1")), T0);

        assertThat(instance.getStatus()).isEqualTo(ProcessStatus.OPEN);
        assertThat(instance.documentStatus()).isEqualTo(ExerciseLifecycle.UNDER_REVIEW);
        assertThat(instance.getCurrentStep()).isEqualTo((short) 1);
        assertThat(instance.getTasks()).hasSize(2);
        assertThat(instance.getTasks().get(0).getNode()).isEqualTo(TaskNode.SUBMIT);
        assertThat(instance.getTasks().get(0).getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(instance.getTasks().get(0).getActors().get(0).getStatus()).isEqualTo(ActorStatus.APPROVED);
        assertThat(instance.findCurrentPendingTask().orElseThrow().getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void orSignCompletesNodeAndCancelsSibling() {
        ProcessInstance instance = openAtManager();
        ProcessTask manager = instance.findCurrentPendingTask().orElseThrow();
        manager.addActor(TaskActor.pending(ActorType.DELEGATE, "P-D", "del1"));
        TaskActor approver = manager.findPendingActor(Set.of("P-M")).orElseThrow();

        instance.approve(approver, "ok", UUID.randomUUID(), T1);

        assertThat(manager.getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(approver.getStatus()).isEqualTo(ActorStatus.APPROVED);
        assertThat(manager.getActors())
                .filteredOn(actor -> actor.getActorType() == ActorType.DELEGATE)
                .extracting(TaskActor::getStatus)
                .containsExactly(ActorStatus.CANCELLED);
        assertThat(instance.getStatus()).isEqualTo(ProcessStatus.OPEN);
    }

    @Test
    void returnFinishesProcessAndAllowsResubmit() {
        ProcessInstance instance = openAtManager();
        TaskActor actor = instance.findCurrentPendingTask().orElseThrow()
                .findAnyPendingActor().orElseThrow();

        instance.returnToSupervisor(actor, "fix kpi", UUID.randomUUID(), T1);

        assertThat(instance.getStatus()).isEqualTo(ProcessStatus.OPEN);
        assertThat(instance.isAwaitingReview()).isFalse();
        assertThat(instance.documentStatus()).isEqualTo(ExerciseLifecycle.IN_PROGRESS);
        assertThat(instance.submissionStatus()).isEqualTo("RETURNED");
        assertThat(instance.isResubmittable()).isTrue();
        assertThat(ExerciseLifecycle.canEdit(instance)).isTrue();
        assertThat(ExerciseLifecycle.canWithdraw(instance)).isFalse();
        assertThat(actor.getStatus()).isEqualTo(ActorStatus.RETURNED);
        assertThat(instance.findCurrentPendingTask()).isEmpty();
    }

    @Test
    void refuseFinishesProcessAndBlocksResubmit() {
        ProcessInstance instance = openAtManager();
        TaskActor actor = instance.findCurrentPendingTask().orElseThrow()
                .findAnyPendingActor().orElseThrow();

        instance.refuse(actor, "out of scope", UUID.randomUUID(), T1);

        assertThat(instance.getStatus()).isEqualTo(ProcessStatus.FINISHED);
        assertThat(instance.documentStatus()).isEqualTo(ExerciseLifecycle.REJECTED);
        assertThat(instance.submissionStatus()).isEqualTo("REJECTED");
        assertThat(instance.isResubmittable()).isFalse();
        assertThat(actor.getStatus()).isEqualTo(ActorStatus.REJECTED);
    }

    @Test
    void withdrawFinishesCurrentReview() {
        ProcessInstance instance = openAtManager();

        instance.withdraw("sup1", UUID.randomUUID(), T1);

        assertThat(instance.getStatus()).isEqualTo(ProcessStatus.OPEN);
        assertThat(instance.isAwaitingReview()).isFalse();
        assertThat(instance.documentStatus()).isEqualTo(ExerciseLifecycle.IN_PROGRESS);
        assertThat(instance.submissionStatus()).isEqualTo("WITHDRAWN");
        assertThat(instance.isResubmittable()).isTrue();
        assertThat(ExerciseLifecycle.canEdit(instance)).isTrue();
        assertThat(ExerciseLifecycle.canWithdraw(instance)).isFalse();
        ProcessTask manager = instance.getTasks().stream()
                .filter(task -> task.getNode() == TaskNode.MANAGER)
                .reduce((a, b) -> b)
                .orElseThrow();
        assertThat(manager.getStatus()).isEqualTo(TaskStatus.WITHDRAWN);
        assertThat(manager.getActors())
                .extracting(TaskActor::getStatus)
                .contains(ActorStatus.WITHDRAWN, ActorStatus.CANCELLED);
    }

    @Test
    void resubmitInsertsNewVisitInsteadOfReusingTask() {
        ProcessInstance instance = openAtManager();
        TaskActor actor = instance.findCurrentPendingTask().orElseThrow()
                .findAnyPendingActor().orElseThrow();
        instance.returnToSupervisor(actor, "fix", UUID.randomUUID(), T1);

        instance.recordSubmit("sup1", "again", UUID.randomUUID(), T1);
        instance.openReview(TaskNode.MANAGER, List.of(new ProcessInstance.Assignee("P-M", "mgr1")), T1);

        assertThat(instance.getStatus()).isEqualTo(ProcessStatus.OPEN);
        assertThat(instance.getTasks().stream().filter(task -> task.getNode() == TaskNode.SUBMIT)).hasSize(2);
        assertThat(instance.getTasks().stream().filter(task -> task.getNode() == TaskNode.MANAGER)).hasSize(2);
        assertThat(instance.findCurrentPendingTask().orElseThrow().getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void lthApproveFinishesAsApproved() {
        ProcessInstance instance = ProcessInstance.start(
                UUID.randomUUID(), null, "sup1", UUID.randomUUID(), T0);
        instance.openReview(TaskNode.LTH, List.of(new ProcessInstance.Assignee("P-L", "lth1")), T0);
        TaskActor actor = instance.findCurrentPendingTask().orElseThrow()
                .findAnyPendingActor().orElseThrow();

        instance.approve(actor, null, UUID.randomUUID(), T1);

        assertThat(instance.getStatus()).isEqualTo(ProcessStatus.FINISHED);
        assertThat(instance.documentStatus()).isEqualTo(ExerciseLifecycle.APPROVED);
        assertThat(instance.submissionStatus()).isEqualTo("APPROVED");
        assertThat(ExerciseLifecycle.canEdit(instance)).isFalse();
    }

    private static ProcessInstance openAtManager() {
        ProcessInstance instance = ProcessInstance.start(
                UUID.randomUUID(), "go", "sup1", UUID.randomUUID(), T0);
        instance.openReview(TaskNode.MANAGER, List.of(new ProcessInstance.Assignee("P-M", "mgr1")), T0);
        return instance;
    }
}
