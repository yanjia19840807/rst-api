package com.cmacgm.gbs.rst.api.toolkit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Canonical slot actual volume for a Toolkit (upserted on Exercise APPROVED). */
@Entity
@Table(name = "toolkit_volume_slot")
public class ToolkitVolumeSlot implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "toolkit_id", nullable = false)
    private UUID toolkitId;

    @Column(name = "slot_start_at", nullable = false)
    private Instant slotStartAt;

    @Column(name = "slot_end_at", nullable = false)
    private Instant slotEndAt;

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

    protected ToolkitVolumeSlot() {
    }

    /**
     * Creates a canonical slot row.
     */
    public static ToolkitVolumeSlot create(
            UUID toolkitId,
            Instant slotStartAt,
            Instant slotEndAt,
            BigDecimal actualVolume,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        ToolkitVolumeSlot row = new ToolkitVolumeSlot();
        row.id = UUID.randomUUID();
        row.toolkitId = toolkitId;
        row.slotStartAt = slotStartAt;
        row.slotEndAt = slotEndAt;
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
    public void replaceFrom(
            Instant slotEndAt,
            BigDecimal actualVolume,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        this.slotEndAt = slotEndAt;
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
    public Instant getSlotStartAt() { return slotStartAt; }
    public Instant getSlotEndAt() { return slotEndAt; }
    public BigDecimal getActualVolume() { return actualVolume; }
    public UUID getSourceExerciseId() { return sourceExerciseId; }
}
