package com.cmacgm.gbs.rst.api.workflow.approval.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Approval tab workspace. {@code IN_PROGRESS} is the current reviewer's turn;
 * {@code COMPLETED} is a finished task (or a read-only Supervisor view).
 *
 * @param mode {@code IN_PROGRESS} or {@code COMPLETED}
 * @param statusBar where the submission is now
 * @param currentHop the hop being decided; null in {@code COMPLETED}
 * @param nextStep hop after Approve; null in {@code COMPLETED}
 * @param nextReviewer occupant of that hop, if known
 * @param history already-occurred Submit / Approve / Return / Withdraw rows
 */
public record ApprovalWorkspaceView(
        String mode,
        ApprovalStatusBar statusBar,
        ApprovalCurrentHop currentHop,
        String nextStep,
        String nextReviewer,
        List<ApprovalHistoryRow> history) {

    /** Status strip: In progress / Now / Archived / Returned / Withdrawn. */
    public record ApprovalStatusBar(
            String state,
            String label,
            String step,
            String reviewer) {
    }

    /** Current hop on the in-progress card (step + occupant). */
    public record ApprovalCurrentHop(String step, String reviewer) {
    }

    /**
     * One occurred workflow action.
     *
     * @param actionId action id
     * @param stepNo 0 for Submit, 1–3 for review hops
     * @param step display name of the hop
     * @param role actor role label
     * @param actor actor display name
     * @param decision Submitted / Approved / Returned / Withdrawn
     * @param comments action comments
     * @param completedAt when the action happened
     * @param mine true when this is the viewer's acted hop (completed mode highlight)
     */
    public record ApprovalHistoryRow(
            UUID actionId,
            short stepNo,
            String step,
            String role,
            String actor,
            String decision,
            String comments,
            Instant completedAt,
            boolean mine) {
    }
}
