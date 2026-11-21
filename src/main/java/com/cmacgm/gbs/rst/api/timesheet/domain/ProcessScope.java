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
 * One process in a snapshot: center × PL3, without a supervisor.
 */
@Entity
@Table(name = "process_scope")
public class ProcessScope implements Persistable<ProcessScope.Id> {

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

    @Transient
    private boolean isNew = true;

    protected ProcessScope() {
    }

    /**
     * Creates a process row.
     *
     * @param syncRunId snapshot
     * @param center GBS center
     * @param pl3Code process level 3
     * @param pl3Name process name
     * @param domain GBS domain
     * @param pl1 process level 1
     * @param pl2 process level 2
     * @return row
     */
    public static ProcessScope create(
            UUID syncRunId,
            String center,
            String pl3Code,
            String pl3Name,
            String domain,
            String pl1,
            String pl2) {
        ProcessScope row = new ProcessScope();
        row.id = new Id(syncRunId, center, pl3Code);
        row.pl3Name = pl3Name;
        row.domain = domain;
        row.pl1 = pl1;
        row.pl2 = pl2;
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

    public String getCenter() {
        return id.center;
    }

    public String getPl3Code() {
        return id.pl3Code;
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

        @Column(nullable = false, length = 120)
        private String center;

        @Column(name = "pl3_code", nullable = false, length = 80)
        private String pl3Code;

        protected Id() {
        }

        public Id(UUID syncRunId, String center, String pl3Code) {
            this.syncRunId = syncRunId;
            this.center = center;
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
                    && Objects.equals(center, that.center)
                    && Objects.equals(pl3Code, that.pl3Code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncRunId, center, pl3Code);
        }
    }
}
