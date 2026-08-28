package com.cmacgm.gbs.rst.api.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RST-owned folders in the Timesheet SharePoint library. Daily / Monthly reports
 * and import templates all live under {@code root}.
 *
 * @param root library-relative path, for example {@code 2.UAT/Data Output/RST}
 */
@ConfigurationProperties(prefix = "rst.sharepoint")
public record RstSharePointProperties(String root) {

    /**
     * @param root RST folder in the Timesheet library
     */
    public RstSharePointProperties {
        if (root == null || root.isBlank()) {
            root = "2.UAT/Data Output/RST";
        } else {
            root = stripSlashes(root);
        }
    }

    /**
     * @return Daily Timesheet folder
     */
    public String dailyFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Daily");
    }

    /**
     * @return Monthly Timesheet folder
     */
    public String monthlyFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Monthly");
    }

    /**
     * @return import Excel template folder
     */
    public String templateFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Template");
    }

    /**
     * @return LTH manual-upload folder
     */
    public String manualFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Manual");
    }

    private static String stripSlashes(String path) {
        String value = path.strip();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
