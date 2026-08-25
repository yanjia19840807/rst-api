package com.cmacgm.gbs.rst.api.exercise.cycletime.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Immutable Cycle Time baseline snapshot; only one active per Exercise. */
@Entity
@Table(name = "cycle_time_baseline")
public class CycleTimeBaseline {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "baseline_type", nullable = false, length = 20)
    private String baselineType;

    @Column(name = "median_seconds", nullable = false, precision = 18, scale = 6)
    private BigDecimal medianSeconds;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "calculation_method", length = 80)
    private String calculationMethod;

    @Column(name = "manual_reason")
    private String manualReason;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "calculated_by")
    private String calculatedBy;

    protected CycleTimeBaseline() {
    }

    /**
     * Creates an active MANUAL baseline.
     *
     * @param exerciseId owning Exercise
     * @param medianSeconds positive median seconds per unit
     * @param manualReason required justification
     * @param actorCcgid creating Supervisor
     * @param now calculation timestamp
     * @return active manual baseline
     */
    public static CycleTimeBaseline createManual(
            UUID exerciseId,
            BigDecimal medianSeconds,
            String manualReason,
            String actorCcgid,
            Instant now) {
        CycleTimeBaseline baseline = new CycleTimeBaseline();
        baseline.id = UUID.randomUUID();
        baseline.exerciseId = exerciseId;
        baseline.baselineType = "MANUAL";
        baseline.medianSeconds = medianSeconds;
        baseline.manualReason = manualReason;
        baseline.calculationMethod = "MANUAL_ENTRY";
        baseline.active = true;
        baseline.calculatedAt = now;
        baseline.calculatedBy = actorCcgid;
        return baseline;
    }

    /**
     * Creates an active SYSTEM baseline from included TMS samples.
     *
     * @param exerciseId owning Exercise
     * @param medianSeconds SYSTEM seconds per unit (session median or sum of subtask medians)
     * @param sampleCount number of included sessions with a valid cycle time
     * @param actorCcgid calculating Supervisor
     * @param now calculation timestamp
     * @return active system baseline
     */
    public static CycleTimeBaseline createSystem(
            UUID exerciseId,
            BigDecimal medianSeconds,
            int sampleCount,
            String actorCcgid,
            Instant now) {
        CycleTimeBaseline baseline = new CycleTimeBaseline();
        baseline.id = UUID.randomUUID();
        baseline.exerciseId = exerciseId;
        baseline.baselineType = "SYSTEM";
        baseline.medianSeconds = medianSeconds;
        baseline.sampleCount = sampleCount;
        baseline.calculationMethod = "MEDIAN";
        baseline.active = true;
        baseline.calculatedAt = now;
        baseline.calculatedBy = actorCcgid;
        return baseline;
    }

    /**
     * Deactivates this baseline when a newer one becomes active.
     */
    public void deactivate() {
        this.active = false;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public String getBaselineType() { return baselineType; }
    public BigDecimal getMedianSeconds() { return medianSeconds; }
    public Integer getSampleCount() { return sampleCount; }
    public String getCalculationMethod() { return calculationMethod; }
    public String getManualReason() { return manualReason; }
    public boolean isActive() { return active; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public String getCalculatedBy() { return calculatedBy; }
}
