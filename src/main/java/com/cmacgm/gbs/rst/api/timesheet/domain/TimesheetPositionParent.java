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
 * One parent edge of a Daily position node. A node may have more than one parent.
 */
@Entity
@Table(name = "timesheet_position_parent")
public class TimesheetPositionParent implements Persistable<TimesheetPositionParent.Id> {

    @EmbeddedId
    private Id id;

    @Transient
    private boolean isNew = true;

    protected TimesheetPositionParent() {
    }

    /**
     * Creates a parent edge.
     *
     * @param syncRunId Daily run
     * @param positionId child position
     * @param roleType child role
     * @param parentPositionId parent position
     * @param parentRoleType parent role
     * @return edge
     */
    public static TimesheetPositionParent create(
            UUID syncRunId,
            String positionId,
            String roleType,
            String parentPositionId,
            String parentRoleType) {
        TimesheetPositionParent row = new TimesheetPositionParent();
        row.id = new Id(syncRunId, positionId, roleType, parentPositionId, parentRoleType);
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
        return id.roleType;
    }

    public String getParentPositionId() {
        return id.parentPositionId;
    }

    public String getParentRoleType() {
        return id.parentRoleType;
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

        @Column(name = "role_type", nullable = false, length = 20)
        private String roleType;

        @Column(name = "parent_position_id", nullable = false, length = 80)
        private String parentPositionId;

        @Column(name = "parent_role_type", nullable = false, length = 20)
        private String parentRoleType;

        protected Id() {
        }

        public Id(
                UUID syncRunId,
                String positionId,
                String roleType,
                String parentPositionId,
                String parentRoleType) {
            this.syncRunId = syncRunId;
            this.positionId = positionId;
            this.roleType = roleType;
            this.parentPositionId = parentPositionId;
            this.parentRoleType = parentRoleType;
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
                    && Objects.equals(positionId, that.positionId)
                    && Objects.equals(roleType, that.roleType)
                    && Objects.equals(parentPositionId, that.parentPositionId)
                    && Objects.equals(parentRoleType, that.parentRoleType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, positionId, roleType, parentPositionId, parentRoleType);
        }
    }
}
