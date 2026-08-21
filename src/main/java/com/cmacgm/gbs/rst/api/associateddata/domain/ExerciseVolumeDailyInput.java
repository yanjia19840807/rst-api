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

/** Daily volume input grain for an Exercise. */
@Entity
@Table(name = "exercise_volume_daily_input")
public class ExerciseVolumeDailyInput implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "volume_date", nullable = false)
    private LocalDate volumeDate;

    @Column(name = "actual_volume", precision = 24, scale = 6)
    private BigDecimal actualVolume;

    @Column(name = "daily_adjustment_ratio", precision = 12, scale = 8)
    private BigDecimal dailyAdjustmentRatio;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

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

    protected ExerciseVolumeDailyInput() {
    }

    /**
     * Creates a daily volume row.
     *
     * @param exerciseId owning Exercise
     * @param volumeDate calendar date
     * @param actualVolume optional actual volume
     * @param dailyAdjustmentRatio Excel Daily Volume Adjustment Ratio; null treated as 0 in sizing
     * @param sourceType MANUAL / ARCHIVE / IMPORT
     * @param importBatchId optional import batch
     * @param actorCcgid creating Supervisor
     * @param now creation timestamp
     * @return new daily volume row
     */
    public static ExerciseVolumeDailyInput create(
            UUID exerciseId,
            LocalDate volumeDate,
            BigDecimal actualVolume,
            BigDecimal dailyAdjustmentRatio,
            String sourceType,
            UUID importBatchId,
            String actorCcgid,
            Instant now) {
        ExerciseVolumeDailyInput row = new ExerciseVolumeDailyInput();
        row.id = UUID.randomUUID();
        row.exerciseId = exerciseId;
        row.volumeDate = volumeDate;
        row.actualVolume = actualVolume;
        row.dailyAdjustmentRatio = dailyAdjustmentRatio;
        row.sourceType = sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType;
        row.importBatchId = importBatchId;
        row.createdAt = now;
        row.createdBy = actorCcgid;
        row.updatedAt = now;
        row.updatedBy = actorCcgid;
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
    public LocalDate getVolumeDate() { return volumeDate; }
    public BigDecimal getActualVolume() { return actualVolume; }
    public BigDecimal getDailyAdjustmentRatio() { return dailyAdjustmentRatio; }
    public String getSourceType() { return sourceType; }
    public UUID getImportBatchId() { return importBatchId; }
}
