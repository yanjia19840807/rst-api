package com.cmacgm.gbs.rst.api.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RST-owned folders in the Timesheet SharePoint library. Daily / Monthly reports
 * and import templates all live under {@code root}.
 *
 * @param root library-relative path, for example {@code 4.RST/2.UAT}
 */
@ConfigurationProperties(prefix = "rst.sharepoint")
public record RstSharePointProperties(String root) {

    /**
     * @param root RST folder in the Timesheet library
     */
    public RstSharePointProperties {
        if (root == null || root.isBlank()) {
            root = "4.RST/2.UAT";
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

    /**
     * @return GBS Process catalog folder (SharePoint download reserved)
     */
    public String processFolder() {
        return MicrosoftGraphPaths.folderPath(root, "Process");
    }

    private static String stripSlashes(String path) {
        String value = path.strip();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
