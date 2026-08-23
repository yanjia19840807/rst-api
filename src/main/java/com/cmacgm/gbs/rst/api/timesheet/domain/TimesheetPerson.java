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
 * One person identity in a Daily sync.
 */
@Entity
@Table(name = "timesheet_person")
public class TimesheetPerson {

    @EmbeddedId
    private Id id;

    @Column(name = "emp_id", length = 80)
    private String empId;

    @Column(name = "emp_position_id", length = 80)
    private String empPositionId;

    @Column(length = 120)
    private String center;

    @Column(nullable = false, length = 200)
    private String name;

    protected TimesheetPerson() {
    }

    /**
     * Creates a person row.
     *
     * @param syncRunId Daily run
     * @param ccgid identity
     * @param empId Timesheet person id
     * @param empPositionId bindable position (emp or occupied management seat)
     * @param center GBS center the person belongs to
     * @param name display name
     * @return row
     */
    public static TimesheetPerson create(
            UUID syncRunId,
            String ccgid,
            String empId,
            String empPositionId,
            String center,
            String name) {
        TimesheetPerson row = new TimesheetPerson();
        row.id = new Id(syncRunId, ccgid);
        row.empId = empId;
        row.empPositionId = empPositionId;
        row.center = center;
        row.name = name;
        return row;
    }

    public Id getId() {
        return id;
    }

    public UUID getSyncRunId() {
        return id.syncRunId;
    }

    public String getCcgid() {
        return id.ccgid;
    }

    public String getEmpId() {
        return empId;
    }

    public String getEmpPositionId() {
        return empPositionId;
    }

    public String getCenter() {
        return center;
    }

    public String getName() {
        return name;
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

        protected Id() {
        }

        public Id(UUID syncRunId, String ccgid) {
            this.syncRunId = syncRunId;
            this.ccgid = ccgid;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(syncRunId, that.syncRunId) && Objects.equals(ccgid, that.ccgid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, ccgid);
        }
    }
}
