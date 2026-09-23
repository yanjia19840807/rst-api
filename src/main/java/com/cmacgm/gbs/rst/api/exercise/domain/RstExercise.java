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

    @Column(name = "slot_start_date")
    private LocalDate slotStartDate;

    @Column(name = "slot_weeks")
    private Short slotWeeks;

    @Column(name = "tms_from")
    private LocalDate tmsFrom;

    @Column(name = "tms_to")
    private LocalDate tmsTo;

    @Column(name = "official_scenario_id")
    private UUID officialScenarioId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "latest_audit_event_id")
    private UUID latestAuditEventId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

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
     * @param slotStartDate optional slot window start (set later in Volume Input)
     * @param slotWeeks optional slot window length in weeks
     * @param tmsFrom optional TMS history from date (set later for SYSTEM median)
     * @param tmsTo optional TMS history to date
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
            Short slotWeeks,
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
        return exercise;
    }

    /**
     * Updates Sizing Month while the Exercise remains editable.
     *
     * @param sizingMonth first day of sizing month
     */
    public void updatePeriods(LocalDate sizingMonth) {
        this.sizingMonth = sizingMonth;
    }

    /**
     * Sets the TMS Period used to link COMPLETED sessions for the SYSTEM median.
     *
     * @param tmsFrom TMS history from date
     * @param tmsTo TMS history to date
     */
    public void updateTmsPeriod(LocalDate tmsFrom, LocalDate tmsTo) {
        this.tmsFrom = tmsFrom;
        this.tmsTo = tmsTo;
    }

    public boolean hasTmsPeriod() {
        return tmsFrom != null && tmsTo != null && !tmsTo.isBefore(tmsFrom);
    }

    /**
     * Clears the TMS Period so SYSTEM median is no longer populated from sessions.
     */
    public void clearTmsPeriod() {
        this.tmsFrom = null;
        this.tmsTo = null;
    }

    /**
     * Sets the Slot Period used to generate Per-slot Volume rows.
     *
     * @param slotStartDate slot window start
     * @param slotWeeks slot window length in weeks (1–12)
     */
    public void updateSlotPeriod(LocalDate slotStartDate, short slotWeeks) {
        if (slotWeeks < 1 || slotWeeks > 12) {
            throw new IllegalArgumentException("Slot weeks must be between 1 and 12.");
        }
        this.slotStartDate = slotStartDate;
        this.slotWeeks = slotWeeks;
    }

    /**
     * Clears the Slot Period so Per-slot Volume is no longer generated.
     */
    public void clearSlotPeriod() {
        this.slotStartDate = null;
        this.slotWeeks = null;
    }

    public boolean hasSlotPeriod() {
        return slotStartDate != null && slotWeeks != null && slotWeeks >= 1;
    }

    /**
     * Marks an unsubmitted Exercise deleted. Who and when are on the audit event.
     */
    public void softDelete() {
        this.deleted = true;
    }

    /**
     * Points the Exercise at its current Official Scenario.
     *
     * @param scenarioId official scenario belonging to this Exercise
     */
    public void setOfficialScenario(UUID scenarioId) {
        this.officialScenarioId = scenarioId;
    }

    /**
     * Clears the current Official Scenario pointer (e.g. after deleting that scenario).
     */
    public void clearOfficialScenario() {
        this.officialScenarioId = null;
    }

    /**
     * Records the submit timestamp. Document status comes from the process.
     *
     * @param now submit timestamp
     */
    public void markSubmitted(Instant now) {
        this.submittedAt = now;
    }

    /**
     * Clears validation after Return so Supervisor can edit again.
     */
    public void markReturned() {
        this.validatedAt = null;
    }

    /**
     * Records LTH approval time. Document status comes from the process.
     *
     * @param now approval timestamp
     */
    public void markApproved(Instant now) {
        this.validatedAt = now;
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

    public void setLatestAuditEventId(UUID latestAuditEventId) {
        this.latestAuditEventId = latestAuditEventId;
    }

    public UUID getLatestAuditEventId() {
        return latestAuditEventId;
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

    public Short getSlotWeeks() {
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

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deleted;
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
