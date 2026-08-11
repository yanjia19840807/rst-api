package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Slot volume input grain for an Exercise. */
@Entity
@Table(name = "exercise_volume_slot_input")
public class ExerciseVolumeSlotInput {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "slot_start_at", nullable = false)
    private Instant slotStartAt;

    @Column(name = "slot_end_at", nullable = false)
    private Instant slotEndAt;

    @Column(name = "raw_volume", nullable = false, precision = 24, scale = 6)
    private BigDecimal rawVolume;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ExerciseVolumeSlotInput() {
    }

    /**
     * Creates a slot volume row.
     *
     * @param exerciseId owning Exercise
     * @param slotStartAt inclusive slot start
     * @param slotEndAt exclusive/end bound (must be after start)
     * @param rawVolume non-negative raw volume
     * @param timezone IANA timezone
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return new slot volume row
     */
    public static ExerciseVolumeSlotInput create(
            UUID exerciseId,
            Instant slotStartAt,
            Instant slotEndAt,
            BigDecimal rawVolume,
            String timezone,
            UUID actorUserId,
            Instant now) {
        return create(
                exerciseId,
                slotStartAt,
                slotEndAt,
                rawVolume,
                timezone,
                "MANUAL",
                null,
                actorUserId,
                now);
    }

    /**
     * Creates a slot volume row with an explicit source and optional import batch.
     *
     * @param sourceType MANUAL / ARCHIVE / IMPORT
     * @param importBatchId optional import batch
     */
    public static ExerciseVolumeSlotInput create(
            UUID exerciseId,
            Instant slotStartAt,
            Instant slotEndAt,
            BigDecimal rawVolume,
            String timezone,
            String sourceType,
            UUID importBatchId,
            UUID actorUserId,
            Instant now) {
        ExerciseVolumeSlotInput row = new ExerciseVolumeSlotInput();
        row.id = UUID.randomUUID();
        row.exerciseId = exerciseId;
        row.slotStartAt = slotStartAt;
        row.slotEndAt = slotEndAt;
        row.rawVolume = rawVolume;
        row.timezone = timezone;
        row.sourceType = sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType;
        row.importBatchId = importBatchId;
        row.createdAt = now;
        row.createdBy = actorUserId;
        row.updatedAt = now;
        row.updatedBy = actorUserId;
        return row;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public Instant getSlotStartAt() { return slotStartAt; }
    public Instant getSlotEndAt() { return slotEndAt; }
    public BigDecimal getRawVolume() { return rawVolume; }
    public String getTimezone() { return timezone; }
    public String getSourceType() { return sourceType; }
}
