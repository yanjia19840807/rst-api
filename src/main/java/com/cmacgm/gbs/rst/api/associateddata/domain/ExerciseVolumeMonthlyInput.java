package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Monthly volume input grain for an Exercise. */
@Entity
@Table(name = "exercise_volume_monthly_input")
public class ExerciseVolumeMonthlyInput {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    /** First day of the month (DATE). */
    @Column(nullable = false)
    private LocalDate month;

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

    protected ExerciseVolumeMonthlyInput() {
    }

    /**
     * Creates a monthly volume row.
     *
     * @param exerciseId owning Exercise
     * @param month first day of the month
     * @param actualVolume optional actual volume
     * @param sourceType MANUAL / ARCHIVE / IMPORT
     * @param importBatchId optional import batch
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return new monthly volume row
     */
    public static ExerciseVolumeMonthlyInput create(
            UUID exerciseId,
            LocalDate month,
            BigDecimal actualVolume,
            String sourceType,
            UUID importBatchId,
            UUID actorUserId,
            Instant now) {  
        ExerciseVolumeMonthlyInput row = new ExerciseVolumeMonthlyInput();
        row.id = UUID.randomUUID();
        row.exerciseId = exerciseId;
        row.month = month;
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
    public LocalDate getMonth() { return month; }
    public BigDecimal getActualVolume() { return actualVolume; }
    public String getSourceType() { return sourceType; }
    public UUID getImportBatchId() { return importBatchId; }
}
