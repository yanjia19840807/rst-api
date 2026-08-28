package com.cmacgm.gbs.rst.api.workflow.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.security.Handler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One person's (or position's) participation on a process task.
 */
@Entity
@Table(name = "task_actor")
public class TaskActor {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private ProcessTask task;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "position_id", length = 80)
    private String positionId;

    @Column(length = 64)
    private String ccgid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActorStatus status;

    private String comments;

    @Column(name = "acted_at")
    private Instant actedAt;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "subject_name", length = 200)
    private String subjectName;

    @Column(name = "acted_by_ccgid", length = 64)
    private String actedByCcgid;

    @Column(name = "acted_by_name", length = 200)
    private String actedByName;

    protected TaskActor() {
    }

    /**
     * Pending approver or delegate for a review node.
     *
     * @param type APPROVER or DELEGATE
     * @param positionId Timesheet position that owns this todo
     * @param ccgid current occupant, display only
     * @return pending actor
     */
    public static TaskActor pending(ActorType type, String positionId, String ccgid) {
        TaskActor actor = new TaskActor();
        actor.id = UUID.randomUUID();
        actor.actorType = type;
        actor.positionId = positionId;
        actor.ccgid = ccgid;
        actor.status = ActorStatus.PENDING;
        return actor;
    }

    /**
     * Supervisor submit recorded on the SUBMIT node.
     *
     * @param ccgid supervisor
     * @param remarks submit remarks
     * @param requestId idempotency key
     * @param now submit time
     * @return completed initiator
     */
    public static TaskActor submit(String ccgid, String remarks, UUID requestId, Instant now) {
        return submit(Handler.self(ccgid, ccgid), remarks, requestId, now);
    }

    /**
     * Supervisor submit recorded on the SUBMIT node.
     *
     * @param handler subject plus optional delegate
     * @param remarks submit remarks
     * @param requestId idempotency key
     * @param now submit time
     * @return completed initiator
     */
    public static TaskActor submit(Handler handler, String remarks, UUID requestId, Instant now) {
        TaskActor actor = new TaskActor();
        actor.id = UUID.randomUUID();
        actor.actorType = ActorType.INITIATOR;
        actor.ccgid = handler.subjectCcgid();
        actor.status = ActorStatus.APPROVED;
        actor.comments = remarks;
        actor.actedAt = now;
        actor.requestId = requestId;
        actor.applyHandler(handler);
        return actor;
    }

    /**
     * Records who performed this action.
     *
     * @param handler subject plus optional delegate
     */
    public void applyHandler(Handler handler) {
        if (handler == null) {
            return;
        }
        if (handler.subjectCcgid() != null) {
            this.ccgid = handler.subjectCcgid();
        }
        this.subjectName = handler.subjectName();
        if (handler.hasActor()) {
            this.actedByCcgid = handler.actorCcgid();
            this.actedByName = handler.actorName();
        }
    }

    /**
     * @return handler snapshot for this actor
     */
    public Handler handler() {
        return new Handler(ccgid, subjectName, actedByCcgid, actedByName);
    }

    /**
     * Formatted display name for history.
     *
     * @param displayNames ccgid → name fallback
     * @return display
     */
    public String handlerDisplayName(Map<String, String> displayNames) {
        return handler().displayName(displayNames);
    }

    /**
     * Records Approve on this pending actor.
     *
     * @param comments optional comments
     * @param requestId idempotency key
     * @param now decision time
     */
    public void approve(String comments, UUID requestId, Instant now) {
        decide(ActorStatus.APPROVED, comments, requestId, now);
    }

    /**
     * Records Return on this pending actor.
     *
     * @param comments required comments
     * @param requestId idempotency key
     * @param now decision time
     */
    public void returnToSupervisor(String comments, UUID requestId, Instant now) {
        decide(ActorStatus.RETURNED, comments, requestId, now);
    }

    /**
     * Records Reject: ends the process with no resubmit.
     *
     * @param comments required comments
     * @param requestId idempotency key
     * @param now decision time
     */
    public void refuse(String comments, UUID requestId, Instant now) {
        decide(ActorStatus.REJECTED, comments, requestId, now);
    }

    /**
     * Records Supervisor Withdraw on the current review task.
     *
     * @param ccgid supervisor
     * @param requestId audit id
     * @param now withdraw time
     */
    public void withdraw(String ccgid, UUID requestId, Instant now) {
        this.ccgid = ccgid;
        this.actorType = ActorType.INITIATOR;
        decide(ActorStatus.WITHDRAWN, null, requestId, now);
    }

    /**
     * Closes a sibling todo after or-sign / return / withdraw.
     */
    public void cancel() {
        if (status.isPending()) {
            this.status = ActorStatus.CANCELLED;
        }
    }

    /**
     * Updates the live occupant when Timesheet / Domain Head mapping changes.
     *
     * @param positionId new position
     * @param ccgid new occupant
     */
    public void remount(String positionId, String ccgid) {
        if (!status.isPending()) {
            return;
        }
        this.positionId = positionId;
        this.ccgid = ccgid;
    }

    private void decide(ActorStatus outcome, String comments, UUID requestId, Instant now) {
        this.status = outcome;
        this.comments = comments;
        this.requestId = requestId;
        this.actedAt = now;
    }

    void attach(ProcessTask task) {
        this.task = task;
    }

    public UUID getId() { return id; }
    public ProcessTask getTask() { return task; }
    public ActorType getActorType() { return actorType; }
    public String getPositionId() { return positionId; }
    public String getCcgid() { return ccgid; }
    public ActorStatus getStatus() { return status; }
    public String getComments() { return comments; }
    public Instant getActedAt() { return actedAt; }
    public UUID getRequestId() { return requestId; }
    public String getSubjectName() { return subjectName; }
    public String getActedByCcgid() { return actedByCcgid; }
    public String getActedByName() { return actedByName; }
}
