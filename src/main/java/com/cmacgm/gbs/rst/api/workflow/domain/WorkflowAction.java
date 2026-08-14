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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only workflow action history row. */
@Entity
@Table(name = "workflow_action")
public class WorkflowAction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(name = "action_seq", nullable = false)
    private int actionSeq;

    @Column(name = "step_no", nullable = false)
    private short stepNo;

    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "actor_ccgid", nullable = false)
    private String actorCcgid;

    @Column(name = "actor_role_code", nullable = false, length = 40)
    private String actorRoleCode;

    private String comments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_snapshot", columnDefinition = "jsonb")
    private String scopeSnapshot;

    @Column(name = "action_at", nullable = false)
    private Instant actionAt;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    protected WorkflowAction() {
    }

    /**
     * Creates a SUBMIT action (step 0).
     *
     * @param actorCcgid submitting Supervisor
     * @param comments optional remarks
     * @param scopeSnapshot JSON scope snapshot
     * @param requestId idempotency / audit request id
     * @param now action timestamp
     * @return action entity
     */
    public static WorkflowAction submit(
            String actorCcgid, String comments, String scopeSnapshot, UUID requestId, Instant now) {
        WorkflowAction action = new WorkflowAction();
        action.id = UUID.randomUUID();
        action.stepNo = 0;
        action.actionType = "SUBMIT";
        action.actorCcgid = actorCcgid;
        action.actorRoleCode = "SUPERVISOR";
        action.comments = comments;
        action.scopeSnapshot = scopeSnapshot;
        action.actionAt = now;
        action.requestId = requestId;
        return action;
    }

    /**
     * Creates an APPROVE action for the current workflow step.
     *
     * @param stepNo acted step number (1–3)
     * @param actorCcgid approving user
     * @param actorRoleCode role used for the step (MANAGER / CDH / LTH)
     * @param comments optional comments
     * @param requestId idempotency / audit request id
     * @param now action timestamp
     * @return action entity
     */
    public static WorkflowAction approve(
            short stepNo,
            String actorCcgid,
            String actorRoleCode,
            String comments,
            UUID requestId,
            Instant now) {
        WorkflowAction action = new WorkflowAction();
        action.id = UUID.randomUUID();
        action.stepNo = stepNo;
        action.actionType = "APPROVE";
        action.actorCcgid = actorCcgid;
        action.actorRoleCode = actorRoleCode;
        action.comments = comments;
        action.actionAt = now;
        action.requestId = requestId;
        return action;
    }

    /**
     * Creates a RETURN action that sends the submission back to the Supervisor.
     *
     * @param stepNo acted step number (1–3)
     * @param actorCcgid returning user
     * @param actorRoleCode role used for the step (MANAGER / CDH / LTH)
     * @param comments required return comments
     * @param requestId idempotency / audit request id
     * @param now action timestamp
     * @return action entity
     */
    public static WorkflowAction returnAction(
            short stepNo,
            String actorCcgid,
            String actorRoleCode,
            String comments,
            UUID requestId,
            Instant now) {
        WorkflowAction action = new WorkflowAction();
        action.id = UUID.randomUUID();
        action.stepNo = stepNo;
        action.actionType = "RETURN";
        action.actorCcgid = actorCcgid;
        action.actorRoleCode = actorRoleCode;
        action.comments = comments;
        action.actionAt = now;
        action.requestId = requestId;
        return action;
    }

    /**
     * Creates a WITHDRAW action recorded against the step that was waiting.
     *
     * @param stepNo current READY step when withdrawn
     * @param actorCcgid withdrawing Supervisor
     * @param requestId audit request id
     * @param now action timestamp
     * @return action entity
     */
    public static WorkflowAction withdraw(
            short stepNo, String actorCcgid, UUID requestId, Instant now) {
        WorkflowAction action = new WorkflowAction();
        action.id = UUID.randomUUID();
        action.stepNo = stepNo;
        action.actionType = "WITHDRAW";
        action.actorCcgid = actorCcgid;
        action.actorRoleCode = "SUPERVISOR";
        action.actionAt = now;
        action.requestId = requestId;
        return action;
    }

    void attach(WorkflowInstance workflowInstance, int actionSeq) {
        this.workflowInstance = workflowInstance;
        this.actionSeq = actionSeq;
    }

    public UUID getId() { return id; }
    public short getStepNo() { return stepNo; }
    public String getActionType() { return actionType; }
    public String getActorCcgid() { return actorCcgid; }
    public String getActorRoleCode() { return actorRoleCode; }
    public String getComments() { return comments; }
    public Instant getActionAt() { return actionAt; }
    public UUID getRequestId() { return requestId; }
    public int getActionSeq() { return actionSeq; }
}
