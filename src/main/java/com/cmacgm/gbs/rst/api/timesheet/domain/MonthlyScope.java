package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * Read model of a Monthly supervisor link joined to its process attributes.
 * Writes go to {@link SupervisorProcess} and {@link ProcessScope}.
 */
@Entity
@Immutable
@Subselect("""
        SELECT link.source_snapshot_id AS source_snapshot_id,
               link.supervisor_position_id AS supervisor_position_id,
               link.pl3_code AS pl3_code,
               link.center AS center,
               process.pl3_name AS pl3_name,
               process.domain AS domain,
               process.pl1 AS pl1,
               process.pl2 AS pl2
        FROM supervisor_process link
        JOIN process_scope process
          ON process.source_snapshot_id = link.source_snapshot_id
         AND process.center = link.center
         AND process.pl3_code = link.pl3_code
        """)
@Synchronize({"supervisor_process", "process_scope"})
public class MonthlyScope {

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

    protected MonthlyScope() {
    }

    /**
     * Builds a row for tests that stub the read model.
     *
     * @param syncRunId snapshot
     * @param supervisorPositionId supervisor position
     * @param pl3Code process level 3
     * @param center GBS center
     * @param pl3Name process name
     * @param domain GBS domain
     * @param pl1 process level 1
     * @param pl2 process level 2
     * @return row
     */
    public static MonthlyScope create(
            UUID syncRunId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            String pl3Name,
            String domain,
            String pl1,
            String pl2) {
        MonthlyScope row = new MonthlyScope();
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
