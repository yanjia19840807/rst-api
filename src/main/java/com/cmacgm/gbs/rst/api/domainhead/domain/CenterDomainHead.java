package com.cmacgm.gbs.rst.api.domainhead.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

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
    private Id id;

    @Column(name = "position_id", nullable = false, length = 80)
    private String positionId;

    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CenterDomainHead() {
    }

    /**
     * Creates or replaces a mapping.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @param positionId bindable Timesheet position
     * @param updatedBy actor ccgid
     * @param now timestamp
     * @return row
     */
    public static CenterDomainHead create(
            String center, String domain, String positionId, String updatedBy, Instant now) {
        CenterDomainHead row = new CenterDomainHead();
        row.id = new Id(center, domain);
        row.positionId = positionId;
        row.updatedBy = updatedBy;
        row.updatedAt = now;
        return row;
    }

    /**
     * Updates the configured position.
     *
     * @param positionId bindable Timesheet position
     * @param updatedBy actor ccgid
     * @param now timestamp
     */
    public void replace(String positionId, String updatedBy, Instant now) {
        this.positionId = positionId;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public String getCenter() {
        return id.center;
    }

    public String getDomain() {
        return id.domain;
    }

    public String getPositionId() {
        return positionId;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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
