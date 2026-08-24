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
 * Employee assigned to a Supervisor position and PL3 in a Monthly sync.
 */
@Entity
@Table(name = "timesheet_assignment")
public class TimesheetAssignment {

    @EmbeddedId
    private Id id;

    @Column(name = "emp_id", length = 80)
    private String empId;

    protected TimesheetAssignment() {
    }

    /**
     * Creates an assignment row.
     *
     * @param syncRunId Monthly run
     * @param empCcgid employee identity
     * @param empId employee Timesheet id
     * @param supervisorPositionId supervisor position
     * @param pl3Code process level 3
     * @return row
     */
    public static TimesheetAssignment create(
            UUID syncRunId,
            String empCcgid,
            String empId,
            String supervisorPositionId,
            String pl3Code) {
        TimesheetAssignment row = new TimesheetAssignment();
        row.id = new Id(syncRunId, empCcgid, supervisorPositionId, pl3Code);
        row.empId = empId;
        return row;
    }

    public UUID getSyncRunId() {
        return id.syncRunId;
    }

    public String getEmpCcgid() {
        return id.empCcgid;
    }

    public String getSupervisorPositionId() {
        return id.supervisorPositionId;
    }

    public String getPl3Code() {
        return id.pl3Code;
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

        @Column(name = "emp_ccgid", nullable = false, length = 32)
        private String empCcgid;

        @Column(name = "supervisor_position_id", nullable = false, length = 80)
        private String supervisorPositionId;

        @Column(name = "pl3_code", nullable = false, length = 80)
        private String pl3Code;

        protected Id() {
        }

        public Id(
                UUID syncRunId, String empCcgid, String supervisorPositionId, String pl3Code) {
            this.syncRunId = syncRunId;
            this.empCcgid = empCcgid;
            this.supervisorPositionId = supervisorPositionId;
            this.pl3Code = pl3Code;
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
                    && Objects.equals(empCcgid, that.empCcgid)
                    && Objects.equals(supervisorPositionId, that.supervisorPositionId)
                    && Objects.equals(pl3Code, that.pl3Code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, empCcgid, supervisorPositionId, pl3Code);
        }
    }
}
