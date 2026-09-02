package com.cmacgm.gbs.rst.api.timesheet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GBS Process catalog used to decide which Timesheet PL3 codes are RST-applicable.
 */
@ConfigurationProperties(prefix = "timesheet.process")
public class TimesheetProcessProperties {

    /**
     * Where to load the catalog. {@code classpath} is the current mock;
     * {@code sharepoint} is reserved for a later Graph download.
     */
    private String source = "classpath";

    /**
     * Classpath location of the mock CSV when {@link #source} is {@code classpath}.
     */
    private String classpathLocation = "GBS Process.csv";

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source == null || source.isBlank() ? "classpath" : source.trim();
    }

    public String getClasspathLocation() {
        return classpathLocation;
    }

    public void setClasspathLocation(String classpathLocation) {
        this.classpathLocation = classpathLocation == null || classpathLocation.isBlank()
                ? "GBS Process.csv"
                : classpathLocation.trim();
    }
}
