package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row config for Timesheet sync failure email alerts.
 */
@Entity
@Table(name = "timesheet_sync_alert")
public class TimesheetSyncAlert {

    public static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private String recipients;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_ccgid", length = 32)
    private String updatedByCcgid;

    protected TimesheetSyncAlert() {
    }

    /**
     * @return default disabled row
     */
    public static TimesheetSyncAlert disabled() {
        TimesheetSyncAlert alert = new TimesheetSyncAlert();
        alert.id = SINGLETON_ID;
        alert.enabled = false;
        alert.recipients = "";
        alert.updatedAt = Instant.EPOCH;
        alert.updatedByCcgid = "SYSTEM";
        return alert;
    }

    /**
     * @param enabled whether alerts are on
     * @param recipients stored recipient text
     * @param updatedAt update time
     * @param updatedByCcgid actor
     */
    public void replace(boolean enabled, String recipients, Instant updatedAt, String updatedByCcgid) {
        this.id = SINGLETON_ID;
        this.enabled = enabled;
        this.recipients = recipients == null ? "" : recipients;
        this.updatedAt = updatedAt;
        this.updatedByCcgid = updatedByCcgid;
    }

    public short getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRecipients() {
        return recipients;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedByCcgid() {
        return updatedByCcgid;
    }
}
