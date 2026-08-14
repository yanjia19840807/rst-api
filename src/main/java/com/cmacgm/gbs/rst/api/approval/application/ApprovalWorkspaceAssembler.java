package com.cmacgm.gbs.rst.api.approval.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView.ApprovalCurrentHop;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView.ApprovalHistoryRow;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView.ApprovalStatusBar;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowAction;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowStepAssignment;
import org.springframework.stereotype.Component;

/**
 * Builds the Approval tab workspace. In-progress and completed layouts are assembled
 * on separate paths so Current / Next never mix with a finished-task highlight.
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
     * Assembles the in-progress workspace (current hop card, Next, past history only).
     *
     * @param submission submission
     * @param workflow workflow instance
     * @param exercise exercise (Timesheet supervisor position)
     * @param displayNames actor ccgid → display name
     * @return in-progress workspace
     */
    public ApprovalWorkspaceView inProgress(
            Submission submission,
            WorkflowInstance workflow,
            RstExercise exercise,
            Map<String, String> displayNames) {
        Waiting waiting = waiting(submission, workflow, exercise, displayNames);
        WorkflowRouter.NextHop next = workflowRouter.previewNext(
                waiting.role(), supervisorPosition(exercise));
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
     * Assembles the completed-task workspace (status strip + full history, highlight mine).
     *
     * @param submission submission
     * @param workflow workflow instance
     * @param exercise exercise
     * @param principal viewer; used only to highlight their acted hop
     * @param displayNames actor ccgid → display name
     * @return completed workspace
     */
    public ApprovalWorkspaceView completed(
            Submission submission,
            WorkflowInstance workflow,
            RstExercise exercise,
            RstPrincipal principal,
            Map<String, String> displayNames) {
        Waiting waiting = waiting(submission, workflow, exercise, displayNames);
        Short mineStep = actedStepNo(workflow, exercise, principal);
        return new ApprovalWorkspaceView(
                MODE_COMPLETED,
                completedStatusBar(submission, waiting),
                null,
                null,
                null,
                history(workflow, displayNames, mineStep));
    }

    private ApprovalStatusBar completedStatusBar(Submission submission, Waiting waiting) {
        if (submission.isOpen()) {
            return new ApprovalStatusBar("NOW", "Now", waiting.step(), waiting.reviewer());
        }
        return switch (submission.getStatus()) {
            case "APPROVED" -> new ApprovalStatusBar("APPROVED", "Approved", null, null);
            case "RETURNED" -> new ApprovalStatusBar("RETURNED", "Returned", null, null);
            case "WITHDRAWN" -> new ApprovalStatusBar("WITHDRAWN", "Withdrawn", null, null);
            default -> new ApprovalStatusBar(
                    "NOW", submission.getStatus(), waiting.step(), waiting.reviewer());
        };
    }

    private Waiting waiting(
            Submission submission,
            WorkflowInstance workflow,
            RstExercise exercise,
            Map<String, String> displayNames) {
        String supervisorPositionId = supervisorPosition(exercise);
        WorkflowStepAssignment ready = workflow.findCurrentReadyStep().orElse(null);
        String role = ready != null
                ? ready.getRequiredRoleCode()
                : roleForStep(workflow.getCurrentStep());
        String step = submission.isOpen() ? reviewStageLabel(role) : null;
        String reviewer = null;
        if (ready != null) {
            String positionId = resolveStepPosition(ready, supervisorPositionId);
            reviewer = firstNonBlank(
                    workflowRouter.occupantName(ready.getRequiredRoleCode(), positionId),
                    ready.getAssigneeCcgid() == null
                            ? null
                            : displayNames.get(ready.getAssigneeCcgid()));
        }
        return new Waiting(role, step, reviewer);
    }

    private List<ApprovalHistoryRow> history(
            WorkflowInstance workflow,
            Map<String, String> displayNames,
            Short mineStep) {
        List<WorkflowAction> actions = new ArrayList<>(workflow.getActions());
        actions.sort(Comparator
                .comparing(WorkflowAction::getActionAt)
                .thenComparingInt(WorkflowAction::getActionSeq));
        List<ApprovalHistoryRow> rows = new ArrayList<>();
        for (WorkflowAction action : actions) {
            String decision = historyDecision(action.getActionType());
            if (decision == null) {
                continue;
            }
            boolean mine = mineStep != null
                    && action.getStepNo() == mineStep
                    && ("APPROVE".equals(action.getActionType())
                            || "RETURN".equals(action.getActionType()));
            String actor = action.getActorCcgid() == null
                    ? null
                    : displayNames.get(action.getActorCcgid());
            rows.add(new ApprovalHistoryRow(
                    action.getId(),
                    action.getStepNo(),
                    historyStep(workflow, action),
                    roleLabel(action.getActorRoleCode()),
                    actor,
                    decision,
                    action.getComments(),
                    action.getActionAt(),
                    mine));
        }
        return List.copyOf(rows);
    }

    private Short actedStepNo(
            WorkflowInstance workflow, RstExercise exercise, RstPrincipal principal) {
        if (principal == null || workflow == null) {
            return null;
        }
        Set<String> positions = workflowRouter.positionsFor(principal);
        if (positions.isEmpty()) {
            return null;
        }
        String supervisorPositionId = supervisorPosition(exercise);
        return workflow.getActions().stream()
                .filter(action -> "APPROVE".equals(action.getActionType())
                        || "RETURN".equals(action.getActionType()))
                .filter(action -> {
                    String assigned = positionForAction(workflow, supervisorPositionId, action);
                    return assigned != null && positions.contains(assigned);
                })
                .map(WorkflowAction::getStepNo)
                .max(Short::compare)
                .orElse(null);
    }

    private String positionForAction(
            WorkflowInstance workflow, String supervisorPositionId, WorkflowAction action) {
        return workflow.getSteps().stream()
                .filter(step -> step.getStepNo() == action.getStepNo())
                .findFirst()
                .map(step -> resolveStepPosition(step, supervisorPositionId))
                .orElseGet(() -> workflowRouter.positionIdOrNull(
                        supervisorPositionId, action.getActorRoleCode()));
    }

    private String resolveStepPosition(WorkflowStepAssignment step, String supervisorPositionId) {
        if (step == null) {
            return null;
        }
        if (hasText(step.getAssigneePositionId())) {
            return step.getAssigneePositionId();
        }
        return workflowRouter.positionIdOrNull(supervisorPositionId, step.getRequiredRoleCode());
    }

    private static String supervisorPosition(RstExercise exercise) {
        if (exercise == null || exercise.getToolkitSnapshot() == null) {
            return null;
        }
        return exercise.getToolkitSnapshot().getSupervisorPositionId();
    }

    /**
     * History Step column: stage where the action was taken.
     * Submit / Withdraw happen on the Supervisor workbench; Approve / Return on a review hop.
     */
    private static String historyStep(WorkflowInstance workflow, WorkflowAction action) {
        if ("SUBMIT".equals(action.getActionType()) || "WITHDRAW".equals(action.getActionType())) {
            return "Supervisor Workbench";
        }
        short stepNo = action.getStepNo();
        return workflow.getSteps().stream()
                .filter(step -> step.getStepNo() == stepNo)
                .findFirst()
                .map(step -> reviewStageLabel(step.getRequiredRoleCode()))
                .orElseGet(() -> {
                    String role = roleForStep(stepNo);
                    return reviewStageLabel(role != null ? role : action.getActorRoleCode());
                });
    }

    private static String historyDecision(String actionType) {
        if (actionType == null) {
            return null;
        }
        return switch (actionType) {
            case "SUBMIT" -> "Submitted";
            case "APPROVE" -> "Approved";
            case "RETURN" -> "Returned";
            case "WITHDRAW" -> "Withdrawn";
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
        if (step == null) {
            return null;
        }
        return switch (step) {
            case 1 -> "MANAGER";
            case 2 -> "CDH";
            case 3 -> "LTH";
            default -> null;
        };
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Waiting(String role, String step, String reviewer) {
    }
}
