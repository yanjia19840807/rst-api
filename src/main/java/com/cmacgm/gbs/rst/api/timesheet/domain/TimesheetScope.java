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
 * Current Supervisor position × PL3 × Center scope from Monthly.
 */
@Entity
@Table(name = "timesheet_scope")
public class TimesheetScope {

    @EmbeddedId
    private Id id;

    @Column(name = "pl3_name", nullable = false, length = 200)
    private String pl3Name;

    @Column(nullable = false, length = 120)
    private String domain;

    @Column(nullable = false, length = 200)
    private String pl1;

    @Column(nullable = false, length = 200)
    private String pl2;

    protected TimesheetScope() {
    }

    /**
     * Creates a scope row.
     *
     * @param syncRunId Monthly run
     * @param supervisorPositionId supervisor position
     * @param pl3Code process level 3
     * @param center GBS center
     * @param pl3Name process name
     * @param domain GBS domain
     * @param pl1 process level 1
     * @param pl2 process level 2
     * @return row
     */
    public static TimesheetScope create(
            UUID syncRunId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            String pl3Name,
            String domain,
            String pl1,
            String pl2) {
        TimesheetScope row = new TimesheetScope();
        row.id = new Id(syncRunId, supervisorPositionId, pl3Code, center);
        row.pl3Name = pl3Name;
        row.domain = domain;
        row.pl1 = pl1;
        row.pl2 = pl2;
        return row;
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

    public String getPl3Name() {
        return pl3Name;
    }

    public String getDomain() {
        return domain;
    }

    public String getPl1() {
        return pl1;
    }

    public String getPl2() {
        return pl2;
    }

    /**
     * Composite key.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "sync_run_id", nullable = false)
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
