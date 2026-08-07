package com.cmacgm.gbs.rst.api.timesheet.config;

import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CLI / one-shot Timesheet sync settings.
 */
@ConfigurationProperties(prefix = "timesheet.sync")
public class TimesheetSyncProperties {

    /**
     * When true, the application runs a sync and exits.
     */
    private boolean enabled;

    /**
     * Spring resource location of the Monthly Report Excel file.
     */
    private String file =
            "classpath:timesheet/Monthly Report of 202606(GBS CHINA Mock).xlsx";

    /**
     * Preferred worksheet name.
     */
    private String sheet = "Mock Data";

    /**
     * Business sync date. Defaults to today when unset.
     */
    private LocalDate date;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getSheet() {
        return sheet;
    }

    public void setSheet(String sheet) {
        this.sheet = sheet;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}
