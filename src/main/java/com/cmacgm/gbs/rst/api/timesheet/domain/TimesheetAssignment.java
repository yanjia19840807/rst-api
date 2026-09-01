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
 * Agent seat assigned to a Supervisor position × PL3 × Center in a Monthly sync.
 */
@Entity
@Table(name = "timesheet_assignment")
public class TimesheetAssignment implements Persistable<TimesheetAssignment.Id> {

    @EmbeddedId
    private Id id;

    /** Assigned-id rows: true until first persist/load so saveAll does not merge+select. */
    @Transient
    private boolean isNew = true;

    protected TimesheetAssignment() {
    }

    /**
     * Creates an assignment row.
     *
     * @param syncRunId Monthly run
     * @param empPositionId Agent seat
     * @param supervisorPositionId supervisor position
     * @param pl3Code process level 3
     * @param center GBS center
     * @return row
     */
    public static TimesheetAssignment create(
            UUID syncRunId,
            String empPositionId,
            String supervisorPositionId,
            String pl3Code,
            String center) {
        TimesheetAssignment row = new TimesheetAssignment();
        row.id = new Id(syncRunId, empPositionId, supervisorPositionId, pl3Code, center);
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

    public String getEmpPositionId() {
        return id.empPositionId;
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

        @Column(name = "sync_run_id", nullable = false)
        private UUID syncRunId;

        @Column(name = "emp_position_id", nullable = false, length = 80)
        private String empPositionId;

        @Column(name = "supervisor_position_id", nullable = false, length = 80)
        private String supervisorPositionId;

        @Column(name = "pl3_code", nullable = false, length = 80)
        private String pl3Code;

        @Column(name = "center", nullable = false, length = 120)
        private String center;

        protected Id() {
        }

        public Id(
                UUID syncRunId,
                String empPositionId,
                String supervisorPositionId,
                String pl3Code,
                String center) {
            this.syncRunId = syncRunId;
            this.empPositionId = empPositionId;
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
                    && Objects.equals(empPositionId, that.empPositionId)
                    && Objects.equals(supervisorPositionId, that.supervisorPositionId)
                    && Objects.equals(pl3Code, that.pl3Code)
                    && Objects.equals(center, that.center);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, empPositionId, supervisorPositionId, pl3Code, center);
        }
    }
}
