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
 * Supervisor / Manager / CDH position tree node in a Daily sync.
 */
@Entity
@Table(name = "timesheet_position")
public class TimesheetPosition implements Persistable<TimesheetPosition.Id> {

    @EmbeddedId
    private Id id;

    @Column(name = "role_type", nullable = false, length = 20)
    private String roleType;

    @Column(name = "parent_position_id", length = 80)
    private String parentPositionId;

    /** Assigned-id rows: true until first persist/load so saveAll does not merge+select. */
    @Transient
    private boolean isNew = true;

    protected TimesheetPosition() {
    }

    /**
     * Creates a position node.
     *
     * @param syncRunId Daily run
     * @param positionId Timesheet position
     * @param roleType AGENT / SUPERVISOR / SR_MANAGER / DOMAIN_HEAD
     * @param parentPositionId parent position
     * @return row
     */
    public static TimesheetPosition create(
            UUID syncRunId, String positionId, String roleType, String parentPositionId) {
        TimesheetPosition row = new TimesheetPosition();
        row.id = new Id(syncRunId, positionId);
        row.roleType = roleType;
        row.parentPositionId = parentPositionId;
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

    public String getPositionId() {
        return id.positionId;
    }

    public String getRoleType() {
        return roleType;
    }

    public String getParentPositionId() {
        return parentPositionId;
    }

    /**
     * Composite key.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "sync_run_id", nullable = false)
        private UUID syncRunId;

        @Column(name = "position_id", nullable = false, length = 80)
        private String positionId;

        protected Id() {
        }

        public Id(UUID syncRunId, String positionId) {
            this.syncRunId = syncRunId;
            this.positionId = positionId;
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
                    && Objects.equals(positionId, that.positionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, positionId);
        }
    }
}
