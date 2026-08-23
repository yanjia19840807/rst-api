package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Current occupant of a management position in a Daily sync.
 */
@Entity
@Table(name = "timesheet_occupancy")
public class TimesheetOccupancy {

    @EmbeddedId
    private Id id;

    @Column(name = "emp_ccgid", nullable = false, length = 32)
    private String empCcgid;

    @Column(name = "emp_id", length = 80)
    private String empId;

    protected TimesheetOccupancy() {
    }

    /**
     * Creates an occupancy row.
     *
     * @param syncRunId Daily run
     * @param positionId occupied position
     * @param empCcgid occupant identity
     * @param empId occupant Timesheet id
     * @return row
     */
    public static TimesheetOccupancy create(
            UUID syncRunId, String positionId, String empCcgid, String empId) {
        TimesheetOccupancy row = new TimesheetOccupancy();
        row.id = new Id(syncRunId, positionId);
        row.empCcgid = empCcgid;
        row.empId = empId;
        return row;
    }

    public UUID getSyncRunId() {
        return id.syncRunId;
    }

    public String getPositionId() {
        return id.positionId;
    }

    public String getEmpCcgid() {
        return empCcgid;
    }

    public String getEmpId() {
        return empId;
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
