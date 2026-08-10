package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Production support activity line under an Exercise.
 */
@Entity
@Table(name = "exercise_production_support_item")
public class ExerciseProductionSupportItem {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "lineage_id", nullable = false)
    private UUID lineageId;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(nullable = false, length = 240)
    private String activity;

    @Column(name = "frequency_code", nullable = false, length = 30)
    private String frequencyCode;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal volume;

    @Column(name = "unit_of_measure", nullable = false, length = 40)
    private String unitOfMeasure;

    @Column(name = "workload_per_unit_minutes", nullable = false, precision = 18, scale = 6)
    private BigDecimal workloadPerUnitMinutes;

    @Column(name = "annual_multiplier", nullable = false, precision = 18, scale = 6)
    private BigDecimal annualMultiplier;

    @Column(name = "workload_per_year_hours", nullable = false, precision = 18, scale = 6)
    private BigDecimal workloadPerYearHours;

    @Column(name = "support_fte", nullable = false, precision = 18, scale = 6)
    private BigDecimal supportFte;

    private String comments;

    @Column(name = "calculation_version", nullable = false, length = 40)
    private String calculationVersion;

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

    protected ExerciseProductionSupportItem() {
    }

    /**
     * Creates a support item and derives annual hours / FTE.
     *
     * <p>Inputs: volume, minutes per unit, annual multiplier.
     * Intent: store derived Support FTE for Official snapshots.
     * Failure: callers must reject non-positive multipliers before create.
     *
     * @param exerciseId owning Exercise
     * @param category support category
     * @param activity activity name
     * @param frequencyCode frequency code
     * @param volume non-negative volume
     * @param unitOfMeasure unit label
     * @param workloadPerUnitMinutes minutes per unit
     * @param annualMultiplier positive annualization factor
     * @param comments optional comments
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return new support item
     */
    public static ExerciseProductionSupportItem create(
            UUID exerciseId,
            String category,
            String activity,
            String frequencyCode,
            BigDecimal volume,
            String unitOfMeasure,
            BigDecimal workloadPerUnitMinutes,
            BigDecimal annualMultiplier,
            String comments,
            UUID actorUserId,
            Instant now) {
        ExerciseProductionSupportItem item = new ExerciseProductionSupportItem();
        item.id = UUID.randomUUID();
        item.exerciseId = exerciseId;
        item.lineageId = item.id;
        item.category = category;
        item.activity = activity;
        item.frequencyCode = frequencyCode;
        item.volume = volume;
        item.unitOfMeasure = unitOfMeasure;
        item.workloadPerUnitMinutes = workloadPerUnitMinutes;
        item.annualMultiplier = annualMultiplier;
        item.comments = comments;
        item.calculationVersion = "v1.2";
        item.createdAt = now;
        item.createdBy = actorUserId;
        item.updatedAt = now;
        item.updatedBy = actorUserId;
        return item;
    }

    /**
     * Copies a support item from an archived Exercise, preserving lineage.
     */
    public static ExerciseProductionSupportItem createFromArchive(
            UUID exerciseId,
            ExerciseProductionSupportItem source,
            BigDecimal annualMultiplier,
            BigDecimal fteAnnualHours,
            UUID actorUserId,
            Instant now) {
        ExerciseProductionSupportItem item = create(
                exerciseId,
                source.getCategory(),
                source.getActivity(),
                source.getFrequencyCode(),
                source.getVolume(),
                source.getUnitOfMeasure(),
                source.getWorkloadPerUnitMinutes(),
                annualMultiplier,
                source.getComments(),
                actorUserId,
                now);
        item.lineageId = source.getLineageId() != null ? source.getLineageId() : source.getId();
        item.applyDerived(annualMultiplier, fteAnnualHours);
        return item;
    }

    /**
     * Updates editable fields and recalculates derived values.
     *
     * @param category support category
     * @param activity activity name
     * @param frequencyCode frequency code
     * @param volume non-negative volume
     * @param unitOfMeasure unit label
     * @param workloadPerUnitMinutes minutes per unit
     * @param annualMultiplier positive annualization factor
     * @param comments optional comments
     * @param actorUserId updating Supervisor
     * @param now update timestamp
     */
    public void update(
            String category,
            String activity,
            String frequencyCode,
            BigDecimal volume,
            String unitOfMeasure,
            BigDecimal workloadPerUnitMinutes,
            BigDecimal annualMultiplier,
            String comments,
            UUID actorUserId,
            Instant now) {
        this.category = category;
        this.activity = activity;
        this.frequencyCode = frequencyCode;
        this.volume = volume;
        this.unitOfMeasure = unitOfMeasure;
        this.workloadPerUnitMinutes = workloadPerUnitMinutes;
        this.annualMultiplier = annualMultiplier;
        this.comments = comments;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Applies annual multiplier and derives Hours/year + FTE (BRD).
     *
     * <p>hours = volume × mins/unit × multiplier / 60;
     * FTE = hours / (workingHours × availability × workingDays × capacityRatio).
     *
     * @param annualMultiplier frequency annualization factor
     * @param fteAnnualHours FTE denominator hours
     */
    public void applyDerived(BigDecimal annualMultiplier, BigDecimal fteAnnualHours) {
        this.annualMultiplier = annualMultiplier;
        this.workloadPerYearHours = volume
                .multiply(workloadPerUnitMinutes)
                .multiply(annualMultiplier)
                .divide(new BigDecimal("60"), 6, RoundingMode.HALF_UP);
        this.supportFte = this.workloadPerYearHours
                .divide(fteAnnualHours, 6, RoundingMode.HALF_UP);
        this.calculationVersion = "v1.2";
    }

    /**
     * Soft-deletes this support item.
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

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public UUID getLineageId() { return lineageId; }
    public String getCategory() { return category; }
    public String getActivity() { return activity; }
    public String getFrequencyCode() { return frequencyCode; }
    public BigDecimal getVolume() { return volume; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getWorkloadPerUnitMinutes() { return workloadPerUnitMinutes; }
    public BigDecimal getAnnualMultiplier() { return annualMultiplier; }
    public BigDecimal getWorkloadPerYearHours() { return workloadPerYearHours; }
    public BigDecimal getSupportFte() { return supportFte; }
    public String getComments() { return comments; }
    public String getCalculationVersion() { return calculationVersion; }
    public Instant getDeletedAt() { return deletedAt; }
}
