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
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;

import com.cmacgm.gbs.rst.api.submission.domain.SubmissionScope;

/**
 * One approval process for an Exercise. Nodes are opened as new {@link ProcessTask} rows.
 */
@Entity
@Table(name = "process_instance")
public class ProcessInstance {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false, unique = true)
    private UUID exerciseId;

    @Column(name = "submitted_by_ccgid", nullable = false)
    private String submittedByCcgid;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessStatus status;

    @Column(name = "current_step")
    private Short currentStep;

    @Version
    private long version;

    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 32)
    private List<SubmissionScope> scopes = new ArrayList<>();

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 32)
    private List<ProcessTask> tasks = new ArrayList<>();

    protected ProcessInstance() {
    }

    /**
     * Opens a process and records the first Submit.
     *
     * @param exerciseId Exercise being submitted
     * @param remarks submit remarks
     * @param actorCcgid supervisor
     * @param requestId idempotency key
     * @param now submit time
     * @return new instance with a completed SUBMIT task
     */
    public static ProcessInstance start(
            UUID exerciseId,
            String remarks,
            String actorCcgid,
            UUID requestId,
            Instant now) {
        ProcessInstance instance = new ProcessInstance();
        instance.id = UUID.randomUUID();
        instance.exerciseId = exerciseId;
        instance.submittedByCcgid = actorCcgid;
        instance.submittedAt = now;
        instance.remarks = remarks;
        instance.status = ProcessStatus.OPEN;
        instance.recordSubmit(actorCcgid, remarks, requestId, now);
        return instance;
    }

    /**
     * Records a Submit (first or after Return / Withdraw).
     *
     * @param actorCcgid supervisor
     * @param remarks remarks
     * @param requestId idempotency key
     * @param now submit time
     */
    public void recordSubmit(String actorCcgid, String remarks, UUID requestId, Instant now) {
        this.status = ProcessStatus.OPEN;
        this.submittedByCcgid = actorCcgid;
        this.submittedAt = now;
        this.remarks = remarks;
        ProcessTask submit = ProcessTask.submit(now);
        addTask(submit);
        submit.addActor(TaskActor.submit(actorCcgid, remarks, requestId, now));
    }

    /**
     * Opens a review node with the given assignees. Each assignee is one todo; or-sign.
     *
     * @param node review node
     * @param assignees position + occupant pairs
     * @param now arrival time
     * @return opened task
     */
    public ProcessTask openReview(TaskNode node, List<Assignee> assignees, Instant now) {
        ProcessTask task = ProcessTask.openReview(node, now);
        addTask(task);
        for (Assignee assignee : assignees) {
            task.addActor(TaskActor.pending(ActorType.APPROVER, assignee.positionId(), assignee.ccgid()));
        }
        this.currentStep = node.order();
        this.status = ProcessStatus.OPEN;
        return task;
    }

    /**
     * Approves as the given pending actor and closes the node when the strategy is met.
     *
     * @param actor deciding actor
     * @param comments optional comments
     * @param requestId idempotency key
     * @param now decision time
     */
    public void approve(TaskActor actor, String comments, UUID requestId, Instant now) {
        actor.approve(comments, requestId, now);
        actor.getTask().applyDecision(actor, now);
        if (actor.getTask().getStatus() == TaskStatus.APPROVED
                && actor.getTask().getNode() == TaskNode.LTH) {
            this.status = ProcessStatus.FINISHED;
        }
    }

    /**
     * Returns the process to the Supervisor. Process finishes; Exercise becomes editable.
     *
     * @param actor deciding actor
     * @param comments required comments
     * @param requestId idempotency key
     * @param now decision time
     */
    public void returnToSupervisor(TaskActor actor, String comments, UUID requestId, Instant now) {
        actor.returnToSupervisor(comments, requestId, now);
        actor.getTask().applyDecision(actor, now);
        this.status = ProcessStatus.FINISHED;
    }

    /**
     * Rejects and ends the process. Exercise is not reopened.
     *
     * @param actor deciding actor
     * @param comments required comments
     * @param requestId idempotency key
     * @param now decision time
     */
    public void refuse(TaskActor actor, String comments, UUID requestId, Instant now) {
        actor.refuse(comments, requestId, now);
        actor.getTask().applyDecision(actor, now);
        this.status = ProcessStatus.FINISHED;
    }

    /**
     * Withdraws an open process from the Supervisor workbench.
     *
     * @param ownerCcgid supervisor
     * @param requestId audit id
     * @param now withdraw time
     */
    public void withdraw(String ownerCcgid, UUID requestId, Instant now) {
        ProcessTask current = findCurrentPendingTask().orElse(null);
        if (current == null) {
            return;
        }
        TaskActor initiator = TaskActor.pending(ActorType.INITIATOR, null, ownerCcgid);
        current.addActor(initiator);
        initiator.withdraw(ownerCcgid, requestId, now);
        current.complete(TaskStatus.WITHDRAWN, now);
        this.status = ProcessStatus.FINISHED;
    }

    /**
     * Document bucket derived from this process.
     *
     * @return UNDER_REVIEW / APPROVED / REJECTED / IN_PROGRESS
     */
    public String documentStatus() {
        if (status == ProcessStatus.OPEN) {
            return ExerciseLifecycle.UNDER_REVIEW;
        }
        return lastReviewOutcome()
                .map(outcome -> switch (outcome) {
                    case APPROVED -> ExerciseLifecycle.APPROVED;
                    case REJECTED -> ExerciseLifecycle.REJECTED;
                    default -> ExerciseLifecycle.IN_PROGRESS;
                })
                .orElse(ExerciseLifecycle.IN_PROGRESS);
    }

    /**
     * Public submissionStatus: OPEN while running, otherwise the last review outcome.
     *
     * @return OPEN / APPROVED / RETURNED / REJECTED / WITHDRAWN
     */
    public String submissionStatus() {
        if (status == ProcessStatus.OPEN) {
            return "OPEN";
        }
        return lastReviewOutcome().map(Enum::name).orElse(ProcessStatus.FINISHED.name());
    }

    /**
     * Whether Supervisor may submit again on this instance.
     *
     * @return true after Return or Withdraw
     */
    public boolean isResubmittable() {
        return status == ProcessStatus.FINISHED
                && lastReviewOutcome().map(TaskStatus::allowsResubmit).orElse(false);
    }

    /**
     * Latest finished review visit (approve / return / reject / withdraw).
     *
     * @return optional outcome
     */
    public Optional<TaskStatus> lastReviewOutcome() {
        return tasks.stream()
                .filter(task -> task.getNode().isReview())
                .filter(task -> !task.getStatus().isPending())
                .reduce((first, second) -> second)
                .map(ProcessTask::getStatus);
    }

    /**
     * Current review node waiting for a decision.
     *
     * @return optional pending review task
     */
    public Optional<ProcessTask> findCurrentPendingTask() {
        return tasks.stream()
                .filter(task -> task.getStatus().isPending() && task.getNode().isReview())
                .reduce((first, second) -> second);
    }

    /**
     * Finds an actor by request id (idempotency).
     *
     * @param requestId request id
     * @return optional actor
     */
    public Optional<TaskActor> findActorByRequestId(UUID requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return tasks.stream()
                .flatMap(task -> task.getActors().stream())
                .filter(actor -> requestId.equals(actor.getRequestId()))
                .findFirst();
    }

    /**
     * Adds an authorization scope snapshot.
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

    private void addTask(ProcessTask task) {
        task.attach(this);
        tasks.add(task);
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public String getSubmittedByCcgid() { return submittedByCcgid; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getRemarks() { return remarks; }
    public ProcessStatus getStatus() { return status; }
    public Short getCurrentStep() { return currentStep; }
    public List<SubmissionScope> getScopes() { return Collections.unmodifiableList(scopes); }
    public List<ProcessTask> getTasks() { return Collections.unmodifiableList(tasks); }

    /**
     * Position occupant assigned to a review todo.
     *
     * @param positionId Timesheet position
     * @param ccgid current occupant
     */
    public record Assignee(String positionId, String ccgid) {
    }
}
