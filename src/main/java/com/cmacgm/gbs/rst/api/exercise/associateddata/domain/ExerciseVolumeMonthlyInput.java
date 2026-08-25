package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

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

/** Monthly volume input grain for an Exercise. */
@Entity
@Table(name = "exercise_volume_monthly_input")
public class ExerciseVolumeMonthlyInput implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    /** First day of the month (DATE). */
    @Column(nullable = false)
    private LocalDate month;

    @Column(name = "actual_volume", precision = 24, scale = 6)
    private BigDecimal actualVolume;

    @Column(name = "commercial_ratio", precision = 12, scale = 8)
    private BigDecimal commercialRatio;

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

    protected ExerciseVolumeMonthlyInput() {
    }

    /**
     * Creates a monthly volume row.
     *
     * @param exerciseId owning Exercise
     * @param month first day of the month
     * @param actualVolume optional actual volume
     * @param commercialRatio Excel Commercial Ratio; null treated as 0 in sizing
     * @param sourceType MANUAL / ARCHIVE / IMPORT
     * @param importBatchId optional import batch
     * @param actorCcgid creating Supervisor
     * @param now creation timestamp
     * @return new monthly volume row
     */
    public static ExerciseVolumeMonthlyInput create(
            UUID exerciseId,
            LocalDate month,
            BigDecimal actualVolume,
            BigDecimal commercialRatio,
            String sourceType,
            UUID importBatchId,
            String actorCcgid,
            Instant now) {
        ExerciseVolumeMonthlyInput row = new ExerciseVolumeMonthlyInput();
        row.id = UUID.randomUUID();
        row.exerciseId = exerciseId;
        row.month = month;
        row.actualVolume = actualVolume;
        row.commercialRatio = commercialRatio;
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
    public LocalDate getMonth() { return month; }
    public BigDecimal getActualVolume() { return actualVolume; }
    public BigDecimal getCommercialRatio() { return commercialRatio; }
    public String getSourceType() { return sourceType; }
    public UUID getImportBatchId() { return importBatchId; }
}
