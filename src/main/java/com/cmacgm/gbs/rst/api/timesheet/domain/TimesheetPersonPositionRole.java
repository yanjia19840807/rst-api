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
 * One Daily occupancy: a person sits on one position node (position + role).
 */
@Entity
@Table(name = "timesheet_person_position_role")
public class TimesheetPersonPositionRole implements Persistable<TimesheetPersonPositionRole.Id> {

    @EmbeddedId
    private Id id;

    @Transient
    private boolean isNew = true;

    protected TimesheetPersonPositionRole() {
    }

    /**
     * Creates an occupancy row.
     *
     * @param syncRunId Daily run
     * @param ccgid occupant
     * @param positionId Timesheet position
     * @param roleType AGENT / SUPERVISOR / SR_MANAGER
     * @return row
     */
    public static TimesheetPersonPositionRole create(
            UUID syncRunId, String ccgid, String positionId, String roleType) {
        TimesheetPersonPositionRole row = new TimesheetPersonPositionRole();
        row.id = new Id(syncRunId, ccgid, positionId, roleType);
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

    public String getCcgid() {
        return id.ccgid;
    }

    public String getPositionId() {
        return id.positionId;
    }

    public String getRoleType() {
        return id.roleType;
    }

    /**
     * Composite key.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "sync_run_id", nullable = false)
        private UUID syncRunId;

        @Column(nullable = false, length = 32)
        private String ccgid;

        @Column(name = "position_id", nullable = false, length = 80)
        private String positionId;

        @Column(name = "role_type", nullable = false, length = 20)
        private String roleType;

        protected Id() {
        }

        public Id(UUID syncRunId, String ccgid, String positionId, String roleType) {
            this.syncRunId = syncRunId;
            this.ccgid = ccgid;
            this.positionId = positionId;
            this.roleType = roleType;
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
                    && Objects.equals(ccgid, that.ccgid)
                    && Objects.equals(positionId, that.positionId)
                    && Objects.equals(roleType, that.roleType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, ccgid, positionId, roleType);
        }
    }
}
