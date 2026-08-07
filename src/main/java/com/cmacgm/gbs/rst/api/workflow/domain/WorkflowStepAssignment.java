package com.cmacgm.gbs.rst.api.workflow.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Materialized queue routing assignment for one workflow step. */
@Entity
@Table(name = "workflow_step_assignment")
public class WorkflowStepAssignment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(name = "step_no", nullable = false)
    private short stepNo;

    @Column(name = "required_role_code", nullable = false, length = 40)
    private String requiredRoleCode;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "routing_status", nullable = false, length = 20)
    private String routingStatus;

    @Column(name = "scope_snapshot_hash", nullable = false, length = 64)
    private String scopeSnapshotHash;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected WorkflowStepAssignment() {
    }

    /**
     * Creates a READY assignment with a resolved assignee.
     *
     * @param stepNo step number (1 Manager, 2 CDH, 3 LTH)
     * @param requiredRoleCode required role
     * @param assigneeUserId resolved assignee
     * @param scopeSnapshotHash hash of submission scopes used for routing
     * @param now resolution timestamp
     * @return assignment entity
     */
    public static WorkflowStepAssignment ready(
            short stepNo,
            String requiredRoleCode,
            UUID assigneeUserId,
            String scopeSnapshotHash,
            Instant now) {
        WorkflowStepAssignment assignment = new WorkflowStepAssignment();
        assignment.id = UUID.randomUUID();
        assignment.stepNo = stepNo;
        assignment.requiredRoleCode = requiredRoleCode;
        assignment.assigneeUserId = assigneeUserId;
        assignment.routingStatus = "READY";
        assignment.scopeSnapshotHash = scopeSnapshotHash;
        assignment.resolvedAt = now;
        return assignment;
    }

    /**
     * Creates a READY Manager step (step 1).
     *
     * @param assigneeUserId Manager assignee
     * @param scopeSnapshotHash scope hash
     * @param now resolution timestamp
     * @return assignment entity
     */
    public static WorkflowStepAssignment readyManager(
            UUID assigneeUserId, String scopeSnapshotHash, Instant now) {
        return ready((short) 1, "MANAGER", assigneeUserId, scopeSnapshotHash, now);
    }

    /**
     * Creates a READY CDH step (step 2).
     *
     * @param assigneeUserId CDH assignee
     * @param scopeSnapshotHash scope hash
     * @param now resolution timestamp
     * @return assignment entity
     */
    public static WorkflowStepAssignment readyCdh(
            UUID assigneeUserId, String scopeSnapshotHash, Instant now) {
        return ready((short) 2, "CDH", assigneeUserId, scopeSnapshotHash, now);
    }

    /**
     * Creates a READY LTH step (step 3).
     *
     * @param assigneeUserId LTH assignee
     * @param scopeSnapshotHash scope hash
     * @param now resolution timestamp
     * @return assignment entity
     */
    public static WorkflowStepAssignment readyLth(
            UUID assigneeUserId, String scopeSnapshotHash, Instant now) {
        return ready((short) 3, "LTH", assigneeUserId, scopeSnapshotHash, now);
    }

    /**
     * Marks this assignment as acted after Approve or Return.
     */
    public void markActed() {
        this.routingStatus = "ACTED";
    }

    /**
     * Marks this assignment invalidated (open step closed without action).
     */
    public void markInvalidated() {
        this.routingStatus = "INVALIDATED";
    }

    /**
     * Returns whether the assignment is still open (PENDING or READY).
     *
     * @return true when open
     */
    public boolean isOpen() {
        return "PENDING".equals(routingStatus) || "READY".equals(routingStatus);
    }

    void attach(WorkflowInstance workflowInstance) {
        this.workflowInstance = workflowInstance;
    }

    public UUID getId() { return id; }
    public short getStepNo() { return stepNo; }
    public String getRequiredRoleCode() { return requiredRoleCode; }
    public UUID getAssigneeUserId() { return assigneeUserId; }
    public String getRoutingStatus() { return routingStatus; }
    public String getScopeSnapshotHash() { return scopeSnapshotHash; }
}
