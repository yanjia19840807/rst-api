package com.cmacgm.gbs.rst.api.domainhead.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * LTH-configured CDH approver for one Center × Domain.
 */
@Entity
@Table(name = "center_domain_head")
public class CenterDomainHead {

    @EmbeddedId
    private Id key;

    @Column(nullable = false, unique = true)
    private UUID id;

    @Column(name = "position_id", length = 80)
    private String positionId;

    @Column(name = "latest_audit_event_id")
    private UUID latestAuditEventId;

    protected CenterDomainHead() {
    }

    /**
     * Creates a mapping. The id is stable for audit even after the position is cleared.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @param positionId bindable Timesheet position
     * @return row
     */
    public static CenterDomainHead create(String center, String domain, String positionId) {
        CenterDomainHead row = new CenterDomainHead();
        row.key = new Id(center, domain);
        row.id = UUID.randomUUID();
        row.positionId = positionId;
        return row;
    }

    /**
     * Updates the configured position.
     *
     * @param positionId bindable Timesheet position
     */
    public void replace(String positionId) {
        this.positionId = positionId;
    }

    /**
     * Clears the configured position. The row and its id stay so the audit remains attached to this Center and Domain.
     */
    public void clear() {
        this.positionId = null;
    }

    public UUID getId() {
        return id;
    }

    public String getCenter() {
        return key.center;
    }

    public String getDomain() {
        return key.domain;
    }

    public String getPositionId() {
        return positionId;
    }

    public void setLatestAuditEventId(UUID latestAuditEventId) {
        this.latestAuditEventId = latestAuditEventId;
    }

    public UUID getLatestAuditEventId() {
        return latestAuditEventId;
    }

    /**
     * Composite key.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(nullable = false, length = 120)
        private String center;

        @Column(nullable = false, length = 120)
        private String domain;

        protected Id() {
        }

        public Id(String center, String domain) {
            this.center = center;
            this.domain = domain;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Id that)) {
                return false;
            }
            return Objects.equals(center, that.center) && Objects.equals(domain, that.domain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(center, domain);
        }
    }
}
