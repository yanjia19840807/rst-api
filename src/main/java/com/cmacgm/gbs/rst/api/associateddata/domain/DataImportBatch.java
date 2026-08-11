package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tracks a volume/holiday template import batch. */
@Entity
@Table(name = "data_import_batch")
public class DataImportBatch {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "import_type", nullable = false, length = 30)
    private String importType;

    @Column(name = "file_artifact_id", nullable = false)
    private UUID fileArtifactId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "accepted_count")
    private Integer acceptedCount;

    @Column(name = "rejected_count")
    private Integer rejectedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_summary", columnDefinition = "jsonb")
    private String validationSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected DataImportBatch() {
    }

    /**
     * Creates an import batch record after validation.
     *
     * @param exerciseId owning Exercise
     * @param importType MONTHLY_VOLUME / DAILY_VOLUME / SLOT_VOLUME / HOLIDAY
     * @param fileArtifactId stored file stub
     * @param status VALIDATED / IMPORTED / REJECTED
     * @param rowCount total rows parsed
     * @param acceptedCount accepted rows
     * @param rejectedCount rejected rows
     * @param validationSummary optional JSON summary
     * @param actorUserId importer
     * @param now creation time
     * @return new batch
     */
    public static DataImportBatch create(
            UUID exerciseId,
            String importType,
            UUID fileArtifactId,
            String status,
            int rowCount,
            int acceptedCount,
            int rejectedCount,
            String validationSummary,
            UUID actorUserId,
            Instant now) {
        DataImportBatch batch = new DataImportBatch();
        batch.id = UUID.randomUUID();
        batch.exerciseId = exerciseId;
        batch.importType = importType;
        batch.fileArtifactId = fileArtifactId;
        batch.status = status;
        batch.rowCount = rowCount;
        batch.acceptedCount = acceptedCount;
        batch.rejectedCount = rejectedCount;
        batch.validationSummary = validationSummary;
        batch.createdAt = now;
        batch.createdBy = actorUserId;
        return batch;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public String getImportType() { return importType; }
    public String getStatus() { return status; }
}
