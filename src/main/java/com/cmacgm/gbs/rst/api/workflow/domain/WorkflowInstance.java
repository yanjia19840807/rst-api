package com.cmacgm.gbs.rst.api.workflow.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Approval workflow instance bound 1:1 to a Submission. */
@Entity
@Table(name = "workflow_instance")
public class WorkflowInstance {

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false, unique = true)
    private UUID submissionId;

    @Column(name = "workflow_version", nullable = false, length = 30)
    private String workflowVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "current_step")
    private Short currentStep;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkflowStepAssignment> steps = new ArrayList<>();

    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkflowAction> actions = new ArrayList<>();

    protected WorkflowInstance() {
    }

    /**
     * Starts an ACTIVE workflow at Manager step 1.
     *
     * @param submissionId bound submission
     * @param now start timestamp
     * @return new workflow instance
     */
    public static WorkflowInstance start(UUID submissionId, Instant now) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.id = UUID.randomUUID();
        instance.submissionId = submissionId;
        instance.workflowVersion = "dev-v1";
        instance.status = "ACTIVE";
        instance.currentStep = 1;
        instance.startedAt = now;
        return instance;
    }

    /**
     * Adds a step assignment.
     *
     * @param assignment step assignment
     */
    public void addStep(WorkflowStepAssignment assignment) {
        assignment.attach(this);
        steps.add(assignment);
    }

    /**
     * Appends a workflow action with the next sequence number.
     *
     * @param action action row
     */
    public void addAction(WorkflowAction action) {
        action.attach(this, actions.size() + 1);
        actions.add(action);
    }

    /**
     * Advances the workflow after a mid-chain Approve: increments current step and adds the next READY assignment.
     *
     * @param nextStep next step assignment (step 2 CDH or step 3 LTH)
     */
    public void advanceAfterApprove(WorkflowStepAssignment nextStep) {
        this.currentStep = nextStep.getStepNo();
        addStep(nextStep);
    }

    /**
     * Completes the workflow after final (LTH) Approve.
     *
     * @param now completion timestamp
     */
    public void complete(Instant now) {
        this.status = "COMPLETED";
        this.completedAt = now;
    }

    /**
     * Marks the workflow RETURNED and invalidates remaining open steps.
     *
     * @param now return timestamp
     */
    public void markReturned(Instant now) {
        this.status = "RETURNED";
        this.completedAt = now;
        invalidateOpenSteps();
    }

    /**
     * Cancels the workflow (Supervisor Withdraw) and invalidates remaining open steps.
     *
     * @param now cancel timestamp
     */
    public void markCancelled(Instant now) {
        this.status = "CANCELLED";
        this.completedAt = now;
        invalidateOpenSteps();
    }

    /**
     * Invalidates all PENDING/READY step assignments that were not acted.
     */
    public void invalidateOpenSteps() {
        for (WorkflowStepAssignment step : steps) {
            if (step.isOpen()) {
                step.markInvalidated();
            }
        }
    }

    /**
     * Finds the READY assignment for the current step.
     *
     * @return optional current READY step
     */
    public Optional<WorkflowStepAssignment> findCurrentReadyStep() {
        if (currentStep == null) {
            return Optional.empty();
        }
        short step = currentStep;
        return steps.stream()
                .filter(s -> s.getStepNo() == step && "READY".equals(s.getRoutingStatus()))
                .findFirst();
    }

    /**
     * Finds an action by request id (idempotency lookup).
     *
     * @param requestId request id
     * @return optional matching action
     */
    public Optional<WorkflowAction> findActionByRequestId(UUID requestId) {
        return actions.stream().filter(a -> requestId.equals(a.getRequestId())).findFirst();
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public String getStatus() { return status; }
    public Short getCurrentStep() { return currentStep; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<WorkflowStepAssignment> getSteps() { return Collections.unmodifiableList(steps); }
    public List<WorkflowAction> getActions() { return Collections.unmodifiableList(actions); }
}
