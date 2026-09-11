package com.cmacgm.gbs.rst.api.timesheet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Quartz schedule for Daily and Monthly SharePoint sync.
 */
@ConfigurationProperties(prefix = "timesheet.sync")
public class TimesheetSyncProperties {

    @NestedConfigurationProperty
    private Job daily = new Job("0 0 6 * * ?");
    @NestedConfigurationProperty
    private Job monthly = new Job("0 30 6 * * ?");

    public Job getDaily() {
        return daily;
    }

    public void setDaily(Job daily) {
        this.daily = daily == null ? new Job("0 0 6 * * ?") : daily;
    }

    public Job getMonthly() {
        return monthly;
    }

    public void setMonthly(Job monthly) {
        this.monthly = monthly == null ? new Job("0 30 6 * * ?") : monthly;
    }

    /**
     * One timed SharePoint sync job.
     */
    public static class Job {

        private boolean enabled;
        private String cron;

        public Job() {
            this("0 0 6 * * ?");
        }

        public Job(String cron) {
            this.cron = cron;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron == null || cron.isBlank() ? null : cron.trim();
        }
    }
}
