package com.cmacgm.gbs.rst.api.toolkit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Latest approved Production Support row for a Toolkit. */
@Entity
@Table(name = "toolkit_production_support_item")
public class ToolkitProductionSupportItem {

    @Id
    private UUID id;

    @Column(name = "toolkit_id", nullable = false)
    private UUID toolkitId;

    @Column(name = "source_exercise_id", nullable = false)
    private UUID sourceExerciseId;

    @Column(name = "lineage_id", nullable = false)
    private UUID lineageId;

    @Column(name = "category_id")
    private UUID categoryId;

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

    protected ToolkitProductionSupportItem() {
    }

    /**
     * Snapshots one Exercise support item onto a Toolkit.
     */
    public static ToolkitProductionSupportItem fromExercise(
            UUID toolkitId,
            UUID sourceExerciseId,
            ExerciseProductionSupportItem source,
            String actorCcgid,
            Instant now) {
        ToolkitProductionSupportItem item = new ToolkitProductionSupportItem();
        item.id = UUID.randomUUID();
        item.toolkitId = toolkitId;
        item.sourceExerciseId = sourceExerciseId;
        item.lineageId = source.getLineageId() != null ? source.getLineageId() : source.getId();
        item.categoryId = source.getCategoryId();
        item.category = source.getCategory();
        item.activity = source.getActivity();
        item.frequencyCode = source.getFrequencyCode();
        item.volume = source.getVolume();
        item.unitOfMeasure = source.getUnitOfMeasure();
        item.workloadPerUnitMinutes = source.getWorkloadPerUnitMinutes();
        item.comments = source.getComments();
        item.createdAt = now;
        item.createdBy = actorCcgid;
        item.updatedAt = now;
        item.updatedBy = actorCcgid;
        return item;
    }

    public UUID getId() { return id; }
    public UUID getToolkitId() { return toolkitId; }
    public UUID getSourceExerciseId() { return sourceExerciseId; }
    public UUID getLineageId() { return lineageId; }
    public UUID getCategoryId() { return categoryId; }
    public String getCategory() { return category; }
    public String getActivity() { return activity; }
    public String getFrequencyCode() { return frequencyCode; }
    public BigDecimal getVolume() { return volume; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getWorkloadPerUnitMinutes() { return workloadPerUnitMinutes; }
    public String getComments() { return comments; }
}
