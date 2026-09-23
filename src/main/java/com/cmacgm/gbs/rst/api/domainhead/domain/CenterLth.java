package com.cmacgm.gbs.rst.api.domainhead.domain;

import java.util.UUID;

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

    @Column(nullable = false, unique = true)
    private UUID id;

    @Column(name = "position_id", length = 80)
    private String positionId;

    @Column(name = "latest_audit_event_id")
    private UUID latestAuditEventId;

    protected CenterLth() {
    }

    /**
     * Creates a mapping. The id is stable for audit even after the position is cleared.
     *
     * @param center GBS center
     * @param positionId bindable Timesheet position
     * @return row
     */
    public static CenterLth create(String center, String positionId) {
        CenterLth row = new CenterLth();
        row.center = center;
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
     * Clears the configured position. The row and its id stay so the audit remains attached to this Center.
     */
    public void clear() {
        this.positionId = null;
    }

    public String getCenter() {
        return center;
    }

    public UUID getId() {
        return id;
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
}
