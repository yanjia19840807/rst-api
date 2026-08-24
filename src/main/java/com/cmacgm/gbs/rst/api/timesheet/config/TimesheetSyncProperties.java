package com.cmacgm.gbs.rst.api.timesheet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timesheet sync CLI settings. SharePoint folders come from {@code rst.sharepoint}.
 */
@ConfigurationProperties(prefix = "timesheet.sync")
public class TimesheetSyncProperties {

    /**
     * When true, the application runs a sync and exits.
     */
    private boolean enabled;

    /**
     * Kind to sync from CLI: daily, monthly, or all.
     */
    private String kind = "all";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
