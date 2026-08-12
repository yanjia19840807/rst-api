package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Daily volume input grain for an Exercise. */
@Entity
@Table(name = "exercise_volume_daily_input")
public class ExerciseVolumeDailyInput {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "volume_date", nullable = false)
    private LocalDate volumeDate;

    @Column(name = "actual_volume", precision = 24, scale = 6)
    private BigDecimal actualVolume;

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

    protected ExerciseVolumeDailyInput() {
    }

    /**
     * Creates a daily volume row.
     *
     * @param exerciseId owning Exercise
     * @param volumeDate calendar date
     * @param actualVolume optional actual volume
     * @param sourceType MANUAL / ARCHIVE / IMPORT
     * @param importBatchId optional import batch
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return new daily volume row
     */
    public static ExerciseVolumeDailyInput create(
            UUID exerciseId,
            LocalDate volumeDate,
            BigDecimal actualVolume,
            String sourceType,
            UUID importBatchId,
            UUID actorUserId,
            Instant now) {
        ExerciseVolumeDailyInput row = new ExerciseVolumeDailyInput();
        row.id = UUID.randomUUID();
        row.exerciseId = exerciseId;
        row.volumeDate = volumeDate;
        row.actualVolume = actualVolume;
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
    public LocalDate getVolumeDate() { return volumeDate; }
    public BigDecimal getActualVolume() { return actualVolume; }
    public String getSourceType() { return sourceType; }
    public UUID getImportBatchId() { return importBatchId; }
}
