package com.cmacgm.gbs.rst.api.workflow.domain;

/**
 * Exercise document bucket derived from the process. Exercise itself stores no status.
 */
public final class ExerciseLifecycle {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private ExerciseLifecycle() {
    }

    /**
     * List / permission bucket.
     *
     * @param process current process, or null when never submitted
     * @return IN_PROGRESS / UNDER_REVIEW / APPROVED / REJECTED
     */
    public static String workflowStatus(ProcessInstance process) {
        if (process == null) {
            return IN_PROGRESS;
        }
        return process.documentStatus();
    }

    /**
     * Last process outcome for the public submissionStatus field.
     *
     * @param process current process, or null when never submitted
     * @return OPEN / APPROVED / RETURNED / REJECTED / WITHDRAWN, or null
     */
    public static String submissionStatus(ProcessInstance process) {
        if (process == null) {
            return null;
        }
        return process.submissionStatus();
    }

    public static boolean canEdit(ProcessInstance process) {
        return IN_PROGRESS.equals(workflowStatus(process));
    }

    public static boolean canDelete(ProcessInstance process) {
        return canEdit(process);
    }

    public static boolean canSubmit(boolean hasOfficial, ProcessInstance process) {
        return hasOfficial && canEdit(process);
    }

    public static boolean canWithdraw(ProcessInstance process) {
        return process != null && process.isAwaitingReview();
    }

    public static boolean isUnderReview(ProcessInstance process) {
        return UNDER_REVIEW.equals(workflowStatus(process));
    }

    public static boolean isApproved(ProcessInstance process) {
        return APPROVED.equals(workflowStatus(process));
    }

    public static boolean isRejected(ProcessInstance process) {
        return REJECTED.equals(workflowStatus(process));
    }

    public static boolean isArchived(ProcessInstance process) {
        return isApproved(process) || isRejected(process);
    }
}
