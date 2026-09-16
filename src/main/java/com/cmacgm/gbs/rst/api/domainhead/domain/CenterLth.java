package com.cmacgm.gbs.rst.api.domainhead.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * LTH-configured Local Transformation Head for one Center.
 */
@Entity
@Table(name = "center_lth")
public class CenterLth {

    @Id
    @Column(name = "center", nullable = false, length = 120)
    private String center;

    @Column(name = "position_id", nullable = false, length = 80)
    private String positionId;

    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CenterLth() {
    }

    /**
     * Creates a mapping.
     *
     * @param center GBS center
     * @param positionId bindable Timesheet position
     * @param updatedBy actor ccgid
     * @param now timestamp
     * @return row
     */
    public static CenterLth create(String center, String positionId, String updatedBy, Instant now) {
        CenterLth row = new CenterLth();
        row.center = center;
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
        return center;
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
}
