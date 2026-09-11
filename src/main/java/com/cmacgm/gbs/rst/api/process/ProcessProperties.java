package com.cmacgm.gbs.rst.api.process;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * GBS Process catalog. {@code remote=false} reads classpath CSV;
 * pre / prod keep {@code remote=true} so the SharePoint list is used.
 */
@ConfigurationProperties(prefix = "process")
public class ProcessProperties {

    private static final String DEFAULT_CLASSPATH = "timesheet/GBS Process.csv";
    private static final String DEFAULT_SITE =
            "https://cmacgmgroup.sharepoint.com/sites/CMA-GlobalBusinessServices";
    private static final String DEFAULT_LIST = "GBS Process";

    /**
     * When true, load from the SharePoint list; otherwise use classpath CSV.
     */
    private boolean remote;

    /**
     * Classpath CSV used only when {@link #isRemote()} is false.
     */
    private String classpathLocation = DEFAULT_CLASSPATH;

    @NestedConfigurationProperty
    private SharePoint sharepoint = new SharePoint();

    public boolean isRemote() {
        return remote;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    public String getClasspathLocation() {
        return classpathLocation;
    }

    public void setClasspathLocation(String classpathLocation) {
        this.classpathLocation = classpathLocation == null || classpathLocation.isBlank()
                ? DEFAULT_CLASSPATH
                : classpathLocation.trim();
    }

    public SharePoint getSharepoint() {
        return sharepoint;
    }

    public void setSharepoint(SharePoint sharepoint) {
        this.sharepoint = sharepoint == null ? new SharePoint() : sharepoint;
    }

    /**
     * SharePoint list that hosts the GBS Process catalog.
     */
    public static class SharePoint {

        private String site = DEFAULT_SITE;
        private String list = DEFAULT_LIST;

        public String getSite() {
            return site;
        }

        public void setSite(String site) {
            this.site = site == null || site.isBlank() ? DEFAULT_SITE : site.trim();
        }

        public String getList() {
            return list;
        }

        public void setList(String list) {
            this.list = list == null || list.isBlank() ? DEFAULT_LIST : list.trim();
        }
    }
}
