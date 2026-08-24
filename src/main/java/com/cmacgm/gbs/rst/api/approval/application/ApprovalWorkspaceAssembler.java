package com.cmacgm.gbs.rst.api.approval.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView.ApprovalCurrentHop;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView.ApprovalHistoryRow;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView.ApprovalStatusBar;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.ActorStatus;
import com.cmacgm.gbs.rst.api.workflow.domain.ActorType;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessTask;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskActor;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskNode;
import org.springframework.stereotype.Component;

/**
 * Builds the Approval tab workspace from process tasks and actors.
 */
@Component
public class ApprovalWorkspaceAssembler {

    static final String MODE_IN_PROGRESS = "IN_PROGRESS";
    static final String MODE_COMPLETED = "COMPLETED";

    private final WorkflowRouter workflowRouter;

    /**
     * Creates the assembler.
     *
     * @param workflowRouter Timesheet position router
     */
    public ApprovalWorkspaceAssembler(WorkflowRouter workflowRouter) {
        this.workflowRouter = workflowRouter;
    }

    /**
     * Assembles the in-progress workspace.
     *
     * @param workflow process instance
     * @param exercise exercise
     * @param displayNames actor ccgid → display name
     * @return in-progress workspace
     */
    public ApprovalWorkspaceView inProgress(
            ProcessInstance workflow,
            RstExercise exercise,
            Map<String, String> displayNames) {
        Waiting waiting = waiting(workflow, exercise, displayNames);
        WorkflowRouter.NextHop next = workflowRouter.previewNext(
                waiting.role(),
                supervisorPosition(exercise),
                toolkitCenter(exercise),
                toolkitDomain(exercise));
        return new ApprovalWorkspaceView(
                MODE_IN_PROGRESS,
                new ApprovalStatusBar(
                        "IN_PROGRESS", "In progress", waiting.step(), waiting.reviewer()),
                new ApprovalCurrentHop(waiting.step(), waiting.reviewer()),
                next.stepLabel(),
                next.reviewerName(),
                history(workflow, displayNames, null));
    }

    /**
     * Assembles the completed-task workspace.
     *
     * @param workflow process instance
     * @param exercise exercise
     * @param principal viewer; used only to highlight their acted hop
     * @param displayNames actor ccgid → display name
     * @return completed workspace
     */
    public ApprovalWorkspaceView completed(
            ProcessInstance workflow,
            RstExercise exercise,
            RstPrincipal principal,
            Map<String, String> displayNames) {
        Waiting waiting = waiting(workflow, exercise, displayNames);
        Short mineStep = actedStepNo(workflow, principal);
        return new ApprovalWorkspaceView(
                MODE_COMPLETED,
                completedStatusBar(workflow, waiting),
                null,
                null,
                null,
                history(workflow, displayNames, mineStep));
    }

    private ApprovalStatusBar completedStatusBar(ProcessInstance workflow, Waiting waiting) {
        if (workflow.isOpen()) {
            return new ApprovalStatusBar("NOW", "Now", waiting.step(), waiting.reviewer());
        }
        return switch (workflow.submissionStatus()) {
            case "APPROVED" -> new ApprovalStatusBar("APPROVED", "Approved", null, null);
            case "RETURNED" -> new ApprovalStatusBar("RETURNED", "Returned", null, null);
            case "REJECTED" -> new ApprovalStatusBar("REJECTED", "Rejected", null, null);
            case "WITHDRAWN" -> new ApprovalStatusBar("WITHDRAWN", "Withdrawn", null, null);
            default -> new ApprovalStatusBar(
                    "NOW", workflow.submissionStatus(), waiting.step(), waiting.reviewer());
        };
    }

    private Waiting waiting(
            ProcessInstance workflow,
            RstExercise exercise,
            Map<String, String> displayNames) {
        ProcessTask ready = workflow.findCurrentPendingTask().orElse(null);
        String role = ready != null
                ? ready.getNode().roleCode()
                : roleForStep(workflow.getCurrentStep());
        String step = workflow.isOpen() ? reviewStageLabel(role) : null;
        String reviewer = null;
        if (ready != null) {
            TaskActor actor = ready.findAnyPendingActor().orElse(null);
            if (actor != null) {
                reviewer = firstNonBlank(
                        workflowRouter.occupantName(ready.getNode().roleCode(), actor.getPositionId()),
                        actor.getCcgid() == null ? null : displayNames.get(actor.getCcgid()));
            }
        }
        return new Waiting(role, step, reviewer);
    }

    private List<ApprovalHistoryRow> history(
            ProcessInstance workflow,
            Map<String, String> displayNames,
            Short mineStep) {
        return workflow.getTasks().stream()
                .flatMap(task -> task.getActors().stream().map(actor -> historyRow(
                        task, actor, displayNames, mineStep)))
                .filter(row -> row != null)
                .sorted(Comparator.comparing(
                        ApprovalHistoryRow::completedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private ApprovalHistoryRow historyRow(
            ProcessTask task,
            TaskActor actor,
            Map<String, String> displayNames,
            Short mineStep) {
        String decision = historyDecision(actor);
        if (decision == null) {
            return null;
        }
        boolean mine = mineStep != null
                && task.getNodeOrder() == mineStep
                && (actor.getStatus() == ActorStatus.APPROVED
                        || actor.getStatus() == ActorStatus.RETURNED
                        || actor.getStatus() == ActorStatus.REJECTED)
                && actor.getActorType() != ActorType.INITIATOR;
        return new ApprovalHistoryRow(
                actor.getId(),
                task.getNodeOrder(),
                historyStep(task, actor),
                roleLabel(task.getNode().roleCode()),
                actor.getCcgid() == null ? null : displayNames.get(actor.getCcgid()),
                decision,
                actor.getComments(),
                actor.getActedAt(),
                mine);
    }

    private Short actedStepNo(ProcessInstance workflow, RstPrincipal principal) {
        if (principal == null || workflow == null) {
            return null;
        }
        Set<String> positions = workflowRouter.positionsFor(principal);
        if (positions.isEmpty()) {
            return null;
        }
        return workflow.getTasks().stream()
                .flatMap(task -> task.getActors().stream()
                        .filter(actor -> actor.getStatus() == ActorStatus.APPROVED
                                || actor.getStatus() == ActorStatus.RETURNED
                                || actor.getStatus() == ActorStatus.REJECTED)
                        .filter(actor -> actor.getActorType() != ActorType.INITIATOR)
                        .filter(actor -> actor.getPositionId() != null
                                && positions.contains(actor.getPositionId()))
                        .map(actor -> task.getNodeOrder()))
                .max(Short::compare)
                .orElse(null);
    }

    private static String historyStep(ProcessTask task, TaskActor actor) {
        if (actor.getActorType() == ActorType.INITIATOR
                || actor.getStatus() == ActorStatus.WITHDRAWN
                || !task.getNode().isReview()) {
            return "Supervisor Workbench";
        }
        return reviewStageLabel(task.getNode().roleCode());
    }

    private static String historyDecision(TaskActor actor) {
        if (!actor.getStatus().isHistory()) {
            return null;
        }
        if (actor.getStatus() == ActorStatus.APPROVED && actor.getActorType() == ActorType.INITIATOR) {
            return "Submitted";
        }
        return switch (actor.getStatus()) {
            case APPROVED -> "Approved";
            case RETURNED -> "Returned";
            case REJECTED -> "Rejected";
            case WITHDRAWN -> "Withdrawn";
            default -> null;
        };
    }

    private static String reviewStageLabel(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "MANAGER" -> "Manager Review";
            case "CDH" -> "Center Delivery Head Review";
            case "LTH" -> "Local Transformation Head Review";
            default -> role;
        };
    }

    private static String roleLabel(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "SUPERVISOR" -> "Supervisor";
            case "MANAGER" -> "Manager";
            case "CDH" -> "Center Delivery Head";
            case "LTH" -> "Local Transformation Head";
            default -> role;
        };
    }

    private static String roleForStep(Short step) {
        TaskNode node = TaskNode.reviewOf(step);
        return node == null ? null : node.roleCode();
    }

    private static String supervisorPosition(RstExercise exercise) {
        if (exercise == null || exercise.getToolkitSnapshot() == null) {
            return null;
        }
        return exercise.getToolkitSnapshot().getSupervisorPositionId();
    }

    private static String toolkitCenter(RstExercise exercise) {
        if (exercise == null || exercise.getToolkitSnapshot() == null) {
            return null;
        }
        return exercise.getToolkitSnapshot().getCenter();
    }

    private static String toolkitDomain(RstExercise exercise) {
        if (exercise == null || exercise.getToolkitSnapshot() == null) {
            return null;
        }
        return exercise.getToolkitSnapshot().getDomain();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private record Waiting(String role, String step, String reviewer) {
    }
}
