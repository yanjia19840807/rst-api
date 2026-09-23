package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Slot volume input grain for an Exercise. */
@Entity
@Table(name = "exercise_volume_slot_input")
public class ExerciseVolumeSlotInput implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "slot_start_at", nullable = false, columnDefinition = "timestamp")
    private LocalDateTime slotStartAt;

    @Column(name = "slot_end_at", nullable = false, columnDefinition = "timestamp")
    private LocalDateTime slotEndAt;

    @Column(name = "actual_volume", precision = 24, scale = 6)
    private BigDecimal actualVolume;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "import_batch_id")
    private UUID importBatchId;





    @Transient
    private boolean isNew = true;

    protected ExerciseVolumeSlotInput() {
    }

    /**
     * Creates a slot volume row.
     *
     * @param exerciseId owning Exercise
     * @param slotStartAt inclusive slot start (Center wall clock)
     * @param slotEndAt exclusive/end bound (must be after start)
     * @param actualVolume non-negative actual volume
     * @param sourceType MANUAL / TOOLKIT / IMPORT
     * @param importBatchId optional import batch
     * @param actorCcgid creating Supervisor
     * @param now creation timestamp
     * @return new slot volume row
     */
    public static ExerciseVolumeSlotInput create(
            UUID exerciseId,
            LocalDateTime slotStartAt,
            LocalDateTime slotEndAt,
            BigDecimal actualVolume,
            String sourceType,
            UUID importBatchId,
            String actorCcgid,
            Instant now) {
        ExerciseVolumeSlotInput row = new ExerciseVolumeSlotInput();
        row.id = UUID.randomUUID();
        row.exerciseId = exerciseId;
        row.slotStartAt = slotStartAt;
        row.slotEndAt = slotEndAt;
        row.actualVolume = actualVolume;
        row.sourceType = sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType;
        row.importBatchId = importBatchId;
        row.isNew = true;
        return row;
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

    public UUID getExerciseId() { return exerciseId; }
    public LocalDateTime getSlotStartAt() { return slotStartAt; }
    public LocalDateTime getSlotEndAt() { return slotEndAt; }
    public BigDecimal getActualVolume() { return actualVolume; }
    public String getSourceType() { return sourceType; }
    public UUID getImportBatchId() { return importBatchId; }
}
