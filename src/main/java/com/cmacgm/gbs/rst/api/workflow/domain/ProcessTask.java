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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

/**
 * One visit to a process node. A later cycle inserts a new row instead of reusing this one.
 */
@Entity
@Table(name = "process_task")
public class ProcessTask {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false)
    private ProcessInstance instance;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_code", nullable = false, length = 20)
    private TaskNode node;

    @Column(name = "node_order", nullable = false)
    private short nodeOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_strategy", nullable = false, length = 10)
    private CompletionStrategy completionStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("actedAt ASC")
    @BatchSize(size = 64)
    private List<TaskActor> actors = new ArrayList<>();

    protected ProcessTask() {
    }

    /**
     * Opens a pending review node (or-sign by default).
     *
     * @param node review node
     * @param now arrival time
     * @return pending task
     */
    public static ProcessTask openReview(TaskNode node, Instant now) {
        return open(node, CompletionStrategy.OR, TaskStatus.PENDING, now, null);
    }

    /**
     * Records the already-completed SUBMIT node.
     *
     * @param now submit time
     * @return completed submit task
     */
    public static ProcessTask submit(Instant now) {
        return open(TaskNode.SUBMIT, CompletionStrategy.OR, TaskStatus.APPROVED, now, now);
    }

    private static ProcessTask open(
            TaskNode node,
            CompletionStrategy strategy,
            TaskStatus status,
            Instant createdAt,
            Instant completedAt) {
        ProcessTask task = new ProcessTask();
        task.id = UUID.randomUUID();
        task.node = node;
        task.nodeOrder = node.order();
        task.completionStrategy = strategy;
        task.status = status;
        task.createdAt = createdAt;
        task.completedAt = completedAt;
        return task;
    }

    /**
     * Adds an actor to this node.
     *
     * @param actor participant
     */
    public void addActor(TaskActor actor) {
        actor.attach(this);
        actors.add(actor);
    }

    /**
     * Completes the node after a deciding actor action.
     *
     * @param now completion time
     */
    public void complete(TaskStatus outcome, Instant now) {
        this.status = outcome;
        this.completedAt = now;
        cancelPendingActors();
    }

    /**
     * Applies or-sign / and-sign after one actor has decided.
     *
     * @param actor the actor that just decided
     * @param now decision time
     */
    public void applyDecision(TaskActor actor, Instant now) {
        if (actor.getStatus() == ActorStatus.RETURNED) {
            complete(TaskStatus.RETURNED, now);
            return;
        }
        if (actor.getStatus() == ActorStatus.REJECTED) {
            complete(TaskStatus.REJECTED, now);
            return;
        }
        if (actor.getStatus() != ActorStatus.APPROVED) {
            return;
        }
        if (completionStrategy == CompletionStrategy.OR || allApproversApproved()) {
            complete(TaskStatus.APPROVED, now);
        }
    }

    private boolean allApproversApproved() {
        return actors.stream()
                .filter(a -> a.getActorType() == ActorType.APPROVER
                        || a.getActorType() == ActorType.DELEGATE)
                .allMatch(a -> a.getStatus() == ActorStatus.APPROVED);
    }

    private void cancelPendingActors() {
        for (TaskActor actor : actors) {
            actor.cancel();
        }
    }

    /**
     * First pending actor assigned to one of the given positions.
     *
     * @param positions Timesheet positions the caller occupies
     * @return optional pending actor
     */
    public Optional<TaskActor> findPendingActor(java.util.Set<String> positions) {
        if (positions == null || positions.isEmpty()) {
            return Optional.empty();
        }
        return actors.stream()
                .filter(actor -> actor.getStatus().isPending())
                .filter(actor -> actor.getPositionId() != null && positions.contains(actor.getPositionId()))
                .findFirst();
    }

    /**
     * A representative pending actor for display (or-sign: any one).
     *
     * @return optional pending actor
     */
    public Optional<TaskActor> findAnyPendingActor() {
        return actors.stream().filter(actor -> actor.getStatus().isPending()).findFirst();
    }

    void attach(ProcessInstance instance) {
        this.instance = instance;
    }

    public UUID getId() { return id; }
    public ProcessInstance getInstance() { return instance; }
    public TaskNode getNode() { return node; }
    public short getNodeOrder() { return nodeOrder; }
    public CompletionStrategy getCompletionStrategy() { return completionStrategy; }
    public TaskStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<TaskActor> getActors() { return Collections.unmodifiableList(actors); }
}
