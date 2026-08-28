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

    /**
     * Recurring Quartz schedule. Operators set this in config, not in the UI.
     */
    private Schedule schedule = new Schedule();

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

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule == null ? new Schedule() : schedule;
    }

    /**
     * Quartz registration for Daily and Monthly SharePoint sync.
     */
    public static class Schedule {

        /**
         * When true, Quartz registers the configured cron jobs.
         */
        private boolean enabled;

        /**
         * Quartz cron for Daily SharePoint sync.
         */
        private String dailyCron = "0 0 6 * * ?";

        /**
         * Quartz cron for Monthly SharePoint sync.
         */
        private String monthlyCron = "0 30 6 * * ?";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDailyCron() {
            return dailyCron;
        }

        public void setDailyCron(String dailyCron) {
            this.dailyCron = dailyCron;
        }

        public String getMonthlyCron() {
            return monthlyCron;
        }

        public void setMonthlyCron(String monthlyCron) {
            this.monthlyCron = monthlyCron;
        }
    }
}
