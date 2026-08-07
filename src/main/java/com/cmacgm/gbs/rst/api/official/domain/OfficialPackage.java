package com.cmacgm.gbs.rst.api.official.domain;

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

/** Immutable Official Package version for an Exercise Official Scenario. */
@Entity
@Table(name = "official_package")
public class OfficialPackage {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "package_version", nullable = false)
    private int packageVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "package_hash", nullable = false, length = 64)
    private String packageHash;

    @Column(name = "forecast_run_id", nullable = false)
    private UUID forecastRunId;

    @Column(name = "monthly_simulation_run_id", nullable = false)
    private UUID monthlySimulationRunId;

    @Column(name = "daily_simulation_run_id")
    private UUID dailySimulationRunId;

    @Column(name = "slot_simulation_run_id", nullable = false)
    private UUID slotSimulationRunId;

    @Column(name = "timesheet_sync_run_id", nullable = false)
    private UUID timesheetSyncRunId;

    @Column(name = "cycle_time_baseline_id", nullable = false)
    private UUID cycleTimeBaselineId;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "officialPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OfficialPackageSection> sections = new ArrayList<>();

    protected OfficialPackage() {
    }

    /**
     * Creates a CREATED current Official Package.
     *
     * @param exerciseId owning Exercise
     * @param scenarioId official scenario
     * @param packageVersion monotonic version
     * @param forecastRunId ACCEPTED forecast
     * @param monthlySimulationRunId ACCEPTED monthly sizing
     * @param dailySimulationRunId optional daily run
     * @param slotSimulationRunId ACCEPTED slot run
     * @param timesheetSyncRunId timesheet snapshot used by KPI lines
     * @param cycleTimeBaselineId active cycle-time baseline
     * @param inputHash input integrity hash
     * @param packageHash package integrity hash
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return new package aggregate
     */
    public static OfficialPackage create(
            UUID exerciseId,
            UUID scenarioId,
            int packageVersion,
            UUID forecastRunId,
            UUID monthlySimulationRunId,
            UUID dailySimulationRunId,
            UUID slotSimulationRunId,
            UUID timesheetSyncRunId,
            UUID cycleTimeBaselineId,
            String inputHash,
            String packageHash,
            UUID actorUserId,
            Instant now) {
        OfficialPackage pkg = new OfficialPackage();
        pkg.id = UUID.randomUUID();
        pkg.exerciseId = exerciseId;
        pkg.scenarioId = scenarioId;
        pkg.packageVersion = packageVersion;
        pkg.status = "CREATED";
        pkg.forecastRunId = forecastRunId;
        pkg.monthlySimulationRunId = monthlySimulationRunId;
        pkg.dailySimulationRunId = dailySimulationRunId;
        pkg.slotSimulationRunId = slotSimulationRunId;
        pkg.timesheetSyncRunId = timesheetSyncRunId;
        pkg.cycleTimeBaselineId = cycleTimeBaselineId;
        pkg.inputHash = inputHash;
        pkg.packageHash = packageHash;
        pkg.current = true;
        pkg.createdAt = now;
        pkg.createdBy = actorUserId;
        return pkg;
    }

    /**
     * Adds an immutable section snapshot.
     *
     * @param sectionType section discriminator
     * @param schemaVersion payload schema version
     * @param payloadJson compact JSON payload
     * @param payloadHash integrity hash
     * @param now creation timestamp
     */
    public void addSection(
            String sectionType, String schemaVersion, String payloadJson, String payloadHash, Instant now) {
        sections.add(OfficialPackageSection.create(this, sectionType, schemaVersion, payloadJson, payloadHash, now));
    }

    /**
     * Marks package submitted.
     */
    public void markSubmitted() {
        this.status = "SUBMITTED";
    }

    /**
     * Marks package returned and clears the current flag.
     */
    public void markReturned() {
        this.status = "RETURNED";
        this.current = false;
    }

    /**
     * Marks package validated after final LTH Approve.
     */
    public void markValidated() {
        this.status = "VALIDATED";
    }

    /**
     * Clears current flag when superseded by a newer package.
     */
    public void clearCurrent() {
        this.current = false;
        this.status = "SUPERSEDED";
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public UUID getScenarioId() { return scenarioId; }
    public int getPackageVersion() { return packageVersion; }
    public String getStatus() { return status; }
    public UUID getForecastRunId() { return forecastRunId; }
    public UUID getMonthlySimulationRunId() { return monthlySimulationRunId; }
    public UUID getDailySimulationRunId() { return dailySimulationRunId; }
    public UUID getSlotSimulationRunId() { return slotSimulationRunId; }
    public UUID getTimesheetSyncRunId() { return timesheetSyncRunId; }
    public UUID getCycleTimeBaselineId() { return cycleTimeBaselineId; }
    public boolean isCurrent() { return current; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OfficialPackageSection> getSections() { return Collections.unmodifiableList(sections); }
}
