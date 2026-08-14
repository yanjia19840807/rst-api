package com.cmacgm.gbs.rst.api.cycletime.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Evidence file attached to a MANUAL cycle-time baseline. */
@Entity
@Table(name = "cycle_time_baseline_file")
@IdClass(CycleTimeBaselineFile.Pk.class)
public class CycleTimeBaselineFile {

    @Id
    @Column(name = "cycle_time_baseline_id", nullable = false)
    private UUID cycleTimeBaselineId;

    @Id
    @Column(name = "file_artifact_id", nullable = false)
    private UUID fileArtifactId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    protected CycleTimeBaselineFile() {
    }

    /**
     * Links a file artifact to a baseline.
     *
     * @param cycleTimeBaselineId baseline id
     * @param fileArtifactId file artifact id
     * @param displayOrder display order
     * @param actorCcgid creating user
     * @param now creation timestamp
     * @return link row
     */
    public static CycleTimeBaselineFile link(
            UUID cycleTimeBaselineId,
            UUID fileArtifactId,
            int displayOrder,
            String actorCcgid,
            Instant now) {
        CycleTimeBaselineFile row = new CycleTimeBaselineFile();
        row.cycleTimeBaselineId = cycleTimeBaselineId;
        row.fileArtifactId = fileArtifactId;
        row.displayOrder = displayOrder;
        row.createdAt = now;
        row.createdBy = actorCcgid;
        return row;
    }

    public UUID getCycleTimeBaselineId() { return cycleTimeBaselineId; }
    public UUID getFileArtifactId() { return fileArtifactId; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }

    /** Composite PK. */
    public static class Pk implements Serializable {
        private UUID cycleTimeBaselineId;
        private UUID fileArtifactId;

        public Pk() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(cycleTimeBaselineId, pk.cycleTimeBaselineId)
                    && Objects.equals(fileArtifactId, pk.fileArtifactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cycleTimeBaselineId, fileArtifactId);
        }
    }
}
