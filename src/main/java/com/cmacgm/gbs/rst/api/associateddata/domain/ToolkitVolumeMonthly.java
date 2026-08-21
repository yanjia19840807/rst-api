package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Canonical monthly actual volume for a Toolkit (upserted on Exercise APPROVED). */
@Entity
@Table(name = "toolkit_volume_monthly")
public class ToolkitVolumeMonthly implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "toolkit_id", nullable = false)
    private UUID toolkitId;

    @Column(nullable = false)
    private LocalDate month;

    @Column(name = "actual_volume", nullable = false, precision = 24, scale = 6)
    private BigDecimal actualVolume;

    @Column(name = "source_exercise_id", nullable = false)
    private UUID sourceExerciseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Transient
    private boolean isNew = true;

    protected ToolkitVolumeMonthly() {
    }

    /**
     * Creates a canonical monthly row.
     */
    public static ToolkitVolumeMonthly create(
            UUID toolkitId,
            LocalDate month,
            BigDecimal actualVolume,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        ToolkitVolumeMonthly row = new ToolkitVolumeMonthly();
        row.id = UUID.randomUUID();
        row.toolkitId = toolkitId;
        row.month = month;
        row.actualVolume = actualVolume;
        row.sourceExerciseId = sourceExerciseId;
        row.createdAt = now;
        row.createdBy = actorCcgid;
        row.updatedAt = now;
        row.updatedBy = actorCcgid;
        row.isNew = true;
        return row;
    }

    /**
     * Overwrites actual from a newly approved Exercise.
     */
    public void replaceFrom(BigDecimal actualVolume, UUID sourceExerciseId, String actorCcgid, Instant now) {
        this.actualVolume = actualVolume;
        this.sourceExerciseId = sourceExerciseId;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getToolkitId() { return toolkitId; }
    public LocalDate getMonth() { return month; }
    public BigDecimal getActualVolume() { return actualVolume; }
    public UUID getSourceExerciseId() { return sourceExerciseId; }
}
