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

    @Column(name = "owner_ccgid", nullable = false)
    private String ownerCcgid;

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
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

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
     * Creates a new Exercise header. Document status is derived from the process
     * (none yet → Supervisor Sizing).
     *
     * @param id Exercise id
     * @param exerciseCode unique business code
     * @param toolkitId source Toolkit id
     * @param ownerCcgid owning Supervisor
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
            String ownerCcgid,
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
        exercise.ownerCcgid = ownerCcgid;
        exercise.sizingMonth = sizingMonth;
        exercise.slotStartDate = slotStartDate;
        exercise.slotWeeks = slotWeeks;
        exercise.tmsFrom = tmsFrom;
        exercise.tmsTo = tmsTo;
        exercise.createdAt = now;
        exercise.createdBy = ownerCcgid;
        exercise.updatedAt = now;
        exercise.updatedBy = ownerCcgid;
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
     * @param actorCcgid updating Supervisor
     * @param now update timestamp
     */
    public void updatePeriods(
            LocalDate sizingMonth,
            LocalDate slotStartDate,
            short slotWeeks,
            LocalDate tmsFrom,
            LocalDate tmsTo,
            String actorCcgid,
            Instant now) {
        this.sizingMonth = sizingMonth;
        this.slotStartDate = slotStartDate;
        this.slotWeeks = slotWeeks;
        this.tmsFrom = tmsFrom;
        this.tmsTo = tmsTo;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Soft-deletes an unsubmitted Exercise.
     *
     * @param actorCcgid deleting Supervisor
     * @param now deletion timestamp
     */
    public void softDelete(String actorCcgid, Instant now) {
        this.deletedAt = now;
        this.deletedBy = actorCcgid;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Points the Exercise at its current Official Scenario.
     *
     * @param scenarioId official scenario belonging to this Exercise
     * @param actorCcgid actor
     * @param now update timestamp
     */
    public void setOfficialScenario(UUID scenarioId, String actorCcgid, Instant now) {
        this.officialScenarioId = scenarioId;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Clears the current Official Scenario pointer (e.g. after deleting that scenario).
     *
     * @param actorCcgid actor
     * @param now update timestamp
     */
    public void clearOfficialScenario(String actorCcgid, Instant now) {
        this.officialScenarioId = null;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Records the submit timestamp. Document status comes from the process.
     *
     * @param actorCcgid submitting Supervisor
     * @param now submit timestamp
     */
    public void markSubmitted(String actorCcgid, Instant now) {
        this.submittedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Clears validation after Return so Supervisor can edit again.
     *
     * @param actorCcgid actor
     * @param now update timestamp
     */
    public void markReturned(String actorCcgid, Instant now) {
        reopenForEditing(actorCcgid, now);
    }

    /**
     * Clears validation after Withdraw while keeping the Official Scenario pointer.
     *
     * @param actorCcgid actor
     * @param now update timestamp
     */
    public void markWithdrawn(String actorCcgid, Instant now) {
        reopenForEditing(actorCcgid, now);
    }

    private void reopenForEditing(String actorCcgid, Instant now) {
        this.validatedAt = null;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Records LTH approval time. Document status comes from the process.
     *
     * @param actorCcgid approving actor
     * @param now approval timestamp
     */
    public void markApproved(String actorCcgid, Instant now) {
        this.validatedAt = now;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Records a terminal Reject. Document status comes from the process.
     *
     * @param actorCcgid acting approver
     * @param now reject time
     */
    public void markRejected(String actorCcgid, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Records that Associated Data was initialized from another Exercise.
     */
    public void markInitializedFrom(UUID sourceExerciseId, String actorCcgid, Instant now) {
        this.initializedFromExerciseId = sourceExerciseId;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    public boolean hasOfficialScenario() {
        return officialScenarioId != null;
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
            String createdBy,
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
            String createdBy,
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

    public String getOwnerCcgid() {
        return ownerCcgid;
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
