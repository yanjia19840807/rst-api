package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/**
 * Monthly link: this supervisor position owns this center's PL3.
 */
@Entity
@Table(name = "supervisor_process")
public class SupervisorProcess implements Persistable<SupervisorProcess.Id> {

    @EmbeddedId
    private Id id;

    @Transient
    private boolean isNew = true;

    protected SupervisorProcess() {
    }

    /**
     * Creates a supervisor-to-process link.
     *
     * @param syncRunId snapshot
     * @param supervisorPositionId supervisor position
     * @param pl3Code process level 3
     * @param center GBS center
     * @return row
     */
    public static SupervisorProcess create(
            UUID syncRunId, String supervisorPositionId, String pl3Code, String center) {
        SupervisorProcess row = new SupervisorProcess();
        row.id = new Id(syncRunId, supervisorPositionId, pl3Code, center);
        row.isNew = true;
        return row;
    }

    @Override
    public Id getId() {
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

    public UUID getSyncRunId() {
        return id.syncRunId;
    }

    public String getSupervisorPositionId() {
        return id.supervisorPositionId;
    }

    public String getPl3Code() {
        return id.pl3Code;
    }

    public String getCenter() {
        return id.center;
    }

    /**
     * Composite key.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "source_snapshot_id", nullable = false)
        private UUID syncRunId;

        @Column(name = "supervisor_position_id", nullable = false, length = 80)
        private String supervisorPositionId;

        @Column(name = "pl3_code", nullable = false, length = 80)
        private String pl3Code;

        @Column(nullable = false, length = 120)
        private String center;

        protected Id() {
        }

        public Id(UUID syncRunId, String supervisorPositionId, String pl3Code, String center) {
            this.syncRunId = syncRunId;
            this.supervisorPositionId = supervisorPositionId;
            this.pl3Code = pl3Code;
            this.center = center;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(syncRunId, that.syncRunId)
                    && Objects.equals(supervisorPositionId, that.supervisorPositionId)
                    && Objects.equals(pl3Code, that.pl3Code)
                    && Objects.equals(center, that.center);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, supervisorPositionId, pl3Code, center);
        }
    }
}
