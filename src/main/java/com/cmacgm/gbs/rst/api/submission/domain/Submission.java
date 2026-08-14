package com.cmacgm.gbs.rst.api.submission.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Submission of an Exercise Official Scenario into the approval workflow. */
@Entity
@Table(name = "submission")
public class Submission {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false, unique = true)
    private UUID exerciseId;

    @Column(name = "submission_code", nullable = false, unique = true, length = 50)
    private String submissionCode;

    @Column(name = "submitted_by_ccgid", nullable = false)
    private String submittedByCcgid;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    private String remarks;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "current_step")
    private Short currentStep;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionScope> scopes = new ArrayList<>();

    protected Submission() {
    }

    /**
     * Creates a submission open for Manager approval (step 1).
     *
     * @param exerciseId Exercise being submitted
     * @param submissionCode unique business code
     * @param remarks optional / required remarks depending on validation
     * @param actorCcgid submitting Supervisor
     * @param now submit timestamp
     * @return new submission
     */
    public static Submission createOpen(
            UUID exerciseId,
            String submissionCode,
            String remarks,
            String actorCcgid,
            Instant now) {
        Submission submission = new Submission();
        submission.id = UUID.randomUUID();
        submission.exerciseId = exerciseId;
        submission.submissionCode = submissionCode;
        submission.submittedByCcgid = actorCcgid;
        submission.submittedAt = now;
        submission.remarks = remarks;
        submission.status = "OPEN";
        submission.currentStep = 1;
        return submission;
    }

    /**
     * Adds an immutable authorization scope snapshot.
     *
     * @param scope scope row
     */
    public void addScope(SubmissionScope scope) {
        scope.attach(this);
        scopes.add(scope);
    }

    /**
     * Clears frozen scopes so a resubmit can snapshot the current KPI lines.
     */
    public void clearScopes() {
        scopes.clear();
    }

    /**
     * Advances submission after a successful Approve at the given step.
     *
     * <p>Steps 1–2 stay {@code OPEN} (queue is owned by Workflow); step 3 → {@code APPROVED}.
     *
     * @param approvedStepNo step that was approved (1–3)
     * @param now validation timestamp when completing at step 3
     */
    public void advanceAfterApprove(short approvedStepNo, Instant now) {
        if (approvedStepNo == 1) {
            this.status = "OPEN";
            this.currentStep = 2;
        } else if (approvedStepNo == 2) {
            this.status = "OPEN";
            this.currentStep = 3;
        } else if (approvedStepNo == 3) {
            this.status = "APPROVED";
            this.currentStep = 3;
            this.validatedAt = now;
        } else {
            throw new IllegalArgumentException("Unsupported approve step: " + approvedStepNo);
        }
    }

    /**
     * Marks the submission returned to the Supervisor.
     *
     * @param now return timestamp
     */
    public void markReturned(Instant now) {
        this.status = "RETURNED";
        this.returnedAt = now;
    }

    /**
     * Reopens a returned/withdrawn submission back to Manager review.
     */
    public void reopenOpen(String remarks, String actorCcgid, Instant now) {
        this.status = "OPEN";
        this.currentStep = 1;
        this.returnedAt = null;
        this.remarks = remarks;
        this.submittedAt = now;
        this.submittedByCcgid = actorCcgid;
    }

    /**
     * Marks the submission withdrawn (Supervisor Withdraw).
     *
     * @param now withdraw timestamp
     */
    public void markWithdrawn(Instant now) {
        this.status = "WITHDRAWN";
        this.returnedAt = now;
    }

    /**
     * Returns whether the submission is still open for approver action.
     *
     * @return true when status is OPEN
     */
    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public String getSubmissionCode() { return submissionCode; }
    public String getSubmittedByCcgid() { return submittedByCcgid; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getRemarks() { return remarks; }
    public String getStatus() { return status; }
    public Short getCurrentStep() { return currentStep; }
    public Instant getReturnedAt() { return returnedAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public List<SubmissionScope> getScopes() { return Collections.unmodifiableList(scopes); }
}
