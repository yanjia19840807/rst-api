package com.cmacgm.gbs.rst.api.timesheet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphPaths;

/**
 * Timesheet document library used by Daily / Monthly / Manual sync and import templates.
 *
 * @param site SharePoint site web URL
 * @param library document library display name
 * @param root library-relative RST folder, for example {@code 4.RST/2.UAT}
 */
@ConfigurationProperties(prefix = "timesheet.sharepoint")
public record TimesheetSharePointProperties(String site, String library, String root) {

    private static final String DEFAULT_SITE =
            "https://cmacgmgroup.sharepoint.com/sites/CMA-SharedKPIAutomation";
    private static final String DEFAULT_LIBRARY = "Timesheet";
    private static final String DEFAULT_ROOT = "4.RST/2.UAT";

    /**
     * @param site site URL
     * @param library library name
     * @param root RST folder
     */
    public TimesheetSharePointProperties {
        site = blankToDefault(site, DEFAULT_SITE);
        library = blankToDefault(library, DEFAULT_LIBRARY);
        root = stripSlashes(blankToDefault(root, DEFAULT_ROOT));
    }

    public String dailyFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Daily");
    }

    public String monthlyFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Monthly");
    }

    public String templateFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Template");
    }

    public String manualFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Manual");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String stripSlashes(String path) {
        String value = path.strip();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
