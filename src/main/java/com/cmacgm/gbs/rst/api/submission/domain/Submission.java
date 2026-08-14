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

/** Submission of an Official Package into the approval workflow. */
@Entity
@Table(name = "submission")
public class Submission {

    @Id
    private UUID id;

    @Column(name = "official_package_id", nullable = false, unique = true)
    private UUID officialPackageId;

    @Column(name = "submission_code", nullable = false, unique = true, length = 50)
    private String submissionCode;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

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
     * Creates a submission awaiting Manager approval (step 1).
     *
     * @param officialPackageId package being submitted
     * @param submissionCode unique business code
     * @param remarks optional / required remarks depending on validation
     * @param actorUserId submitting Supervisor
     * @param now submit timestamp
     * @return new submission
     */
    public static Submission createAwaitingManager(
            UUID officialPackageId,
            String submissionCode,
            String remarks,
            UUID actorUserId,
            Instant now) {
        Submission submission = new Submission();
        submission.id = UUID.randomUUID();
        submission.officialPackageId = officialPackageId;
        submission.submissionCode = submissionCode;
        submission.submittedBy = actorUserId;
        submission.submittedAt = now;
        submission.remarks = remarks;
        submission.status = "AWAITING_MANAGER";
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
     * Advances submission status after a successful Approve at the given step.
     *
     * <p>Step 1 → {@code AWAITING_CDH}, step 2 → {@code AWAITING_LTH}, step 3 → {@code VALIDATED}.
     *
     * @param approvedStepNo step that was approved (1–3)
     * @param now validation timestamp when completing at step 3
     */
    public void advanceAfterApprove(short approvedStepNo, Instant now) {
        if (approvedStepNo == 1) {
            this.status = "AWAITING_CDH";
            this.currentStep = 2;
        } else if (approvedStepNo == 2) {
            this.status = "AWAITING_LTH";
            this.currentStep = 3;
        } else if (approvedStepNo == 3) {
            this.status = "VALIDATED";
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
    public void reopenAwaitingManager(String remarks, UUID actorUserId, Instant now) {
        this.status = "AWAITING_MANAGER";
        this.currentStep = 1;
        this.returnedAt = null;
        this.remarks = remarks;
        this.submittedAt = now;
        this.submittedBy = actorUserId;
    }

    /**
     * Marks the submission archived (Supervisor Withdraw).
     *
     * @param now archive timestamp
     */
    public void markArchived(Instant now) {
        this.status = "ARCHIVED";
        this.returnedAt = now;
    }

    /**
     * Returns whether the submission is still awaiting an approver action.
     *
     * @return true for AWAITING_* statuses
     */
    public boolean isAwaitingReview() {
        return status != null && status.startsWith("AWAITING_");
    }

    public UUID getId() { return id; }
    public UUID getOfficialPackageId() { return officialPackageId; }
    public String getSubmissionCode() { return submissionCode; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getRemarks() { return remarks; }
    public String getStatus() { return status; }
    public Short getCurrentStep() { return currentStep; }
    public Instant getReturnedAt() { return returnedAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public List<SubmissionScope> getScopes() { return Collections.unmodifiableList(scopes); }
}
