package com.cmacgm.gbs.rst.api.domainhead.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One Domain Head candidate for a Center × Domain. The workflow still assigns
 * a single handler when the previous step is approved.
 */
@Entity
@Table(name = "center_domain_head_assignee")
public class CenterDomainHeadAssignee {

    @EmbeddedId
    private Id key;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CenterDomainHeadAssignee() {
    }

    /**
     * Creates one candidate.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @param positionId bindable Timesheet position
     * @param sortOrder display order, starting at 0
     * @return row
     */
    public static CenterDomainHeadAssignee create(String center, String domain, String positionId, int sortOrder) {
        CenterDomainHeadAssignee row = new CenterDomainHeadAssignee();
        row.key = new Id(center, domain, positionId);
        row.sortOrder = sortOrder;
        return row;
    }

    public String getCenter() {
        return key.center;
    }

    public String getDomain() {
        return key.domain;
    }

    public String getPositionId() {
        return key.positionId;
    }

    public int getSortOrder() {
        return sortOrder;
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

        @Column(name = "position_id", nullable = false, length = 80)
        private String positionId;

        protected Id() {
        }

        public Id(String center, String domain, String positionId) {
            this.center = center;
            this.domain = domain;
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
            return Objects.equals(center, that.center)
                    && Objects.equals(domain, that.domain)
                    && Objects.equals(positionId, that.positionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(center, domain, positionId);
        }
    }
}
