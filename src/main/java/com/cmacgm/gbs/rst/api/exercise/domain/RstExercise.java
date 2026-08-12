package com.cmacgm.gbs.rst.api.exercise.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Exercise aggregate root: header, frozen Toolkit snapshot, and workflow status.
 */
@Entity
@Table(name = "rst_exercise")
public class RstExercise {

    @Id
    private UUID id;

    @Column(name = "exercise_code", nullable = false, unique = true, length = 50)
    private String exerciseCode;

    @Column(name = "toolkit_id", nullable = false)
    private UUID toolkitId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    /** First day of the sizing month (DATE). */
    @Column(name = "sizing_month", nullable = false)
    private LocalDate sizingMonth;

    @Column(name = "slot_start_date", nullable = false)
    private LocalDate slotStartDate;

    @Column(name = "slot_weeks", nullable = false)
    private short slotWeeks;

    @Column(name = "tms_from", nullable = false)
    private LocalDate tmsFrom;

    @Column(name = "tms_to", nullable = false)
    private LocalDate tmsTo;

    @Column(name = "workflow_status", nullable = false, length = 30)
    private String workflowStatus;

    @Column(name = "official_scenario_id")
    private UUID officialScenarioId;

    @Column(name = "initialized_from_exercise_id")
    private UUID initializedFromExerciseId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private long version;

    @OneToOne(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ExerciseToolkitSnapshot toolkitSnapshot;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<ExerciseSubtask> subtasks = new ArrayList<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("customerCountry ASC, carrier ASC, site ASC")
    private List<ExerciseSharedKpiLine> sharedKpiLines = new ArrayList<>();

    protected RstExercise() {
    }

    /**
     * Creates a new IN_PROGRESS Exercise header.
     *
     * @param id Exercise id
     * @param exerciseCode unique business code
     * @param toolkitId source Toolkit id
     * @param ownerUserId owning Supervisor
     * @param sizingMonth first day of sizing month
     * @param slotStartDate slot window start
     * @param slotWeeks slot window length in weeks
     * @param tmsFrom TMS history from date
     * @param tmsTo TMS history to date
     * @param now creation timestamp
     * @return new Exercise aggregate
     */
    public static RstExercise create(
            UUID id,
            String exerciseCode,
            UUID toolkitId,
            UUID ownerUserId,
            LocalDate sizingMonth,
            LocalDate slotStartDate,
            short slotWeeks,
            LocalDate tmsFrom,
            LocalDate tmsTo,
            Instant now) {
        RstExercise exercise = new RstExercise();
        exercise.id = id;
        exercise.exerciseCode = exerciseCode;
        exercise.toolkitId = toolkitId;
        exercise.ownerUserId = ownerUserId;
        exercise.sizingMonth = sizingMonth;
        exercise.slotStartDate = slotStartDate;
        exercise.slotWeeks = slotWeeks;
        exercise.tmsFrom = tmsFrom;
        exercise.tmsTo = tmsTo;
        exercise.workflowStatus = "IN_PROGRESS";
        exercise.createdAt = now;
        exercise.createdBy = ownerUserId;
        exercise.updatedAt = now;
        exercise.updatedBy = ownerUserId;
        return exercise;
    }

    /**
     * Updates sizing / slot / TMS period fields while the Exercise remains editable.
     *
     * @param sizingMonth first day of sizing month
     * @param slotStartDate slot window start
     * @param slotWeeks slot window length in weeks
     * @param tmsFrom TMS history from date
     * @param tmsTo TMS history to date
     * @param actorUserId updating Supervisor
     * @param now update timestamp
     */
    public void updatePeriods(
            LocalDate sizingMonth,
            LocalDate slotStartDate,
            short slotWeeks,
            LocalDate tmsFrom,
            LocalDate tmsTo,
            UUID actorUserId,
            Instant now) {
        this.sizingMonth = sizingMonth;
        this.slotStartDate = slotStartDate;
        this.slotWeeks = slotWeeks;
        this.tmsFrom = tmsFrom;
        this.tmsTo = tmsTo;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Soft-deletes an unsubmitted Exercise.
     *
     * @param actorUserId deleting Supervisor
     * @param now deletion timestamp
     */
    public void softDelete(UUID actorUserId, Instant now) {
        this.deletedAt = now;
        this.deletedBy = actorUserId;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Points the Exercise at its current Official Scenario.
     *
     * @param scenarioId official scenario belonging to this Exercise
     * @param actorUserId actor
     * @param now update timestamp
     */
    public void setOfficialScenario(UUID scenarioId, UUID actorUserId, Instant now) {
        this.officialScenarioId = scenarioId;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Clears the current Official Scenario pointer (e.g. after Return).
     *
     * @param actorUserId actor
     * @param now update timestamp
     */
    public void clearOfficialScenario(UUID actorUserId, Instant now) {
        this.officialScenarioId = null;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Marks the Exercise UNDER_REVIEW after a successful Submit.
     *
     * @param actorUserId submitting Supervisor
     * @param now submit timestamp
     */
    public void markSubmitted(UUID actorUserId, Instant now) {
        this.workflowStatus = "UNDER_REVIEW";
        this.submittedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Reopens the Exercise after Return/Withdraw: clears Official pointer, clears validatedAt,
     * and sets workflow status to {@code IN_PROGRESS} for Supervisor editing.
     *
     * @param actorUserId actor
     * @param now update timestamp
     */
    public void markReturned(UUID actorUserId, Instant now) {
        this.officialScenarioId = null;
        this.validatedAt = null;
        this.workflowStatus = "IN_PROGRESS";
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Marks the Exercise VALIDATED after final LTH Approve.
     *
     * @param actorUserId validating actor
     * @param now validation timestamp
     */
    public void markValidated(UUID actorUserId, Instant now) {
        this.workflowStatus = "VALIDATED";
        this.validatedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Marks the Exercise ARCHIVED.
     *
     * @param actorUserId actor
     * @param now archive timestamp
     */
    public void markArchived(UUID actorUserId, Instant now) {
        this.workflowStatus = "ARCHIVED";
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Records that Associated Data was initialized from another Exercise.
     */
    public void markInitializedFrom(UUID sourceExerciseId, UUID actorUserId, Instant now) {
        this.initializedFromExerciseId = sourceExerciseId;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Returns whether soft-delete is allowed for the current workflow status.
     *
     * @return true when status is IN_PROGRESS or RETURNED
     */
    public boolean canDelete() {
        return "IN_PROGRESS".equals(workflowStatus) || "RETURNED".equals(workflowStatus);
    }

    /**
     * Returns whether Submit is allowed (has Official Scenario and not yet submitted).
     *
     * @return true when Official exists and status is still editable
     */
    public boolean canSubmit() {
        return officialScenarioId != null
                && ("IN_PROGRESS".equals(workflowStatus) || "RETURNED".equals(workflowStatus));
    }

    /**
     * Returns whether Associated Data / Scenario edits are allowed.
     *
     * @return true for IN_PROGRESS or RETURNED
     */
    public boolean canEdit() {
        return "IN_PROGRESS".equals(workflowStatus) || "RETURNED".equals(workflowStatus);
    }

    /**
     * Returns whether Supervisor Withdraw is allowed.
     *
     * @return true when UNDER_REVIEW
     */
    public boolean canWithdraw() {
        return "UNDER_REVIEW".equals(workflowStatus);
    }

    void attachToolkitSnapshot(ExerciseToolkitSnapshot snapshot) {
        this.toolkitSnapshot = snapshot;
    }

    public void freezeToolkitSnapshot(
            UUID sourceToolkitId,
            long sourceToolkitVersion,
            UUID timesheetSyncRunId,
            String toolkitName,
            String supervisorPositionId,
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            boolean combineSubtasksTime,
            UUID createdBy,
            Instant now) {
        ExerciseToolkitSnapshot.capture(
                this,
                sourceToolkitId,
                sourceToolkitVersion,
                timesheetSyncRunId,
                toolkitName,
                supervisorPositionId,
                center,
                domain,
                pl1,
                pl2,
                pl3Code,
                pl3Name,
                combineSubtasksTime,
                createdBy,
                now);
    }

    public void addSubtask(
            UUID sourceToolkitSubtaskId,
            String name,
            String description,
            int displayOrder,
            Instant now) {
        subtasks.add(ExerciseSubtask.freeze(
                this, sourceToolkitSubtaskId, name, description, displayOrder, now));
    }

    public void addSharedKpiLine(
            UUID toolkitSharedKpiSelectionId,
            UUID timesheetSyncRunId,
            String center,
            String site,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            String carrier,
            String customerCountry,
            BigDecimal deliveryHc,
            UUID createdBy,
            Instant now) {
        sharedKpiLines.add(ExerciseSharedKpiLine.freeze(
                this,
                toolkitSharedKpiSelectionId,
                timesheetSyncRunId,
                center,
                site,
                domain,
                pl1,
                pl2,
                pl3Code,
                pl3Name,
                carrier,
                customerCountry,
                deliveryHc,
                createdBy,
                now));
    }

    public UUID getId() {
        return id;
    }

    public String getExerciseCode() {
        return exerciseCode;
    }

    public UUID getToolkitId() {
        return toolkitId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public LocalDate getSizingMonth() {
        return sizingMonth;
    }

    public LocalDate getSlotStartDate() {
        return slotStartDate;
    }

    public short getSlotWeeks() {
        return slotWeeks;
    }

    public LocalDate getTmsFrom() {
        return tmsFrom;
    }

    public LocalDate getTmsTo() {
        return tmsTo;
    }

    public String getWorkflowStatus() {
        return workflowStatus;
    }

    public UUID getOfficialScenarioId() {
        return officialScenarioId;
    }

    public UUID getInitializedFromExerciseId() {
        return initializedFromExerciseId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }

    public ExerciseToolkitSnapshot getToolkitSnapshot() {
        return toolkitSnapshot;
    }

    public List<ExerciseSubtask> getSubtasks() {
        return Collections.unmodifiableList(subtasks);
    }

    public List<ExerciseSharedKpiLine> getSharedKpiLines() {
        return Collections.unmodifiableList(sharedKpiLines);
    }
}
