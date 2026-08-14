package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Production support activity line under an Exercise.
 * Only Supervisor inputs are persisted; annual hours / FTE are computed on read.
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

    private String comments;

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

    protected ExerciseProductionSupportItem() {
    }

    /**
     * Creates a support item from Supervisor inputs.
     */
    public static ExerciseProductionSupportItem create(
            UUID exerciseId,
            String category,
            String activity,
            String frequencyCode,
            BigDecimal volume,
            String unitOfMeasure,
            BigDecimal workloadPerUnitMinutes,
            String comments,
            String actorCcgid,
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
        item.comments = comments;
        item.createdAt = now;
        item.createdBy = actorCcgid;
        item.updatedAt = now;
        item.updatedBy = actorCcgid;
        return item;
    }

    /**
     * Copies a support item from an archived Exercise, preserving lineage.
     */
    public static ExerciseProductionSupportItem createFromArchive(
            UUID exerciseId,
            ExerciseProductionSupportItem source,
            String actorCcgid,
            Instant now) {
        ExerciseProductionSupportItem item = create(
                exerciseId,
                source.getCategory(),
                source.getActivity(),
                source.getFrequencyCode(),
                source.getVolume(),
                source.getUnitOfMeasure(),
                source.getWorkloadPerUnitMinutes(),
                source.getComments(),
                actorCcgid,
                now);
        item.lineageId = source.getLineageId() != null ? source.getLineageId() : source.getId();
        return item;
    }

    /**
     * Updates editable Supervisor inputs.
     */
    public void update(
            String category,
            String activity,
            String frequencyCode,
            BigDecimal volume,
            String unitOfMeasure,
            BigDecimal workloadPerUnitMinutes,
            String comments,
            String actorCcgid,
            Instant now) {
        this.category = category;
        this.activity = activity;
        this.frequencyCode = frequencyCode;
        this.volume = volume;
        this.unitOfMeasure = unitOfMeasure;
        this.workloadPerUnitMinutes = workloadPerUnitMinutes;
        this.comments = comments;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Soft-deletes this support item.
     */
    public void softDelete(String actorCcgid, Instant now) {
        this.deletedAt = now;
        this.deletedBy = actorCcgid;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
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
    public String getComments() { return comments; }
    public Instant getDeletedAt() { return deletedAt; }
}
