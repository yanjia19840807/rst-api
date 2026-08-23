package com.cmacgm.gbs.rst.api.timesheet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timesheet sync settings for file and SharePoint sources.
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

    /**
     * file or graph.
     */
    private String source = "file";

    /**
     * Local Daily file location.
     */
    private String dailyFile =
            "file:../rst-material/Timesheet/Daily Report of 20260727(GBS CHINA).xlsx";

    /**
     * Local Monthly file location.
     */
    private String monthlyFile =
            "file:../rst-material/Timesheet/Monthly Report of 202606(GBS CHINA).xlsx";

    /**
     * SharePoint folder under env-prefix for Daily files.
     */
    private String dailyFolder = "Data Input/Daily";

    /**
     * SharePoint folder under env-prefix for Monthly files.
     */
    private String monthlyFolder = "Data Input/Monthly";

    /**
     * When true, a scheduled poller checks SharePoint folders.
     */
    private boolean scheduleEnabled;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDailyFile() {
        return dailyFile;
    }

    public void setDailyFile(String dailyFile) {
        this.dailyFile = dailyFile;
    }

    public String getMonthlyFile() {
        return monthlyFile;
    }

    public void setMonthlyFile(String monthlyFile) {
        this.monthlyFile = monthlyFile;
    }

    public String getDailyFolder() {
        return dailyFolder;
    }

    public void setDailyFolder(String dailyFolder) {
        this.dailyFolder = dailyFolder;
    }

    public String getMonthlyFolder() {
        return monthlyFolder;
    }

    public void setMonthlyFolder(String monthlyFolder) {
        this.monthlyFolder = monthlyFolder;
    }

    public boolean isScheduleEnabled() {
        return scheduleEnabled;
    }

    public void setScheduleEnabled(boolean scheduleEnabled) {
        this.scheduleEnabled = scheduleEnabled;
    }
}
