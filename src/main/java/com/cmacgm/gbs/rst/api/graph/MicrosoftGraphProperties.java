package com.cmacgm.gbs.rst.api.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Microsoft Graph client-credentials settings. Production injects client id and secret from
 * the named Azure / Kubernetes secret; local runs use {@code MS_GRAPH_*} environment variables.
 *
 * @param enabled when false, Graph calls fail fast without contacting Microsoft
 * @param secretName ops secret name that holds Graph credentials
 * @param tenantId Azure AD tenant
 * @param clientId application (client) id
 * @param clientSecret application client secret
 * @param sharepointSite SharePoint site web URL
 * @param listName document library / list name on that site
 */
@ConfigurationProperties(prefix = "microsoft.graph")
public record MicrosoftGraphProperties(
        boolean enabled,
        String secretName,
        String tenantId,
        String clientId,
        String clientSecret,
        String sharepointSite,
        String listName) {

    /**
     * Fills Graph defaults when a field is blank.
     *
     * @param enabled Graph enable flag
     * @param secretName credential secret name
     * @param tenantId Azure tenant
     * @param clientId application id
     * @param clientSecret application secret
     * @param sharepointSite SharePoint site URL
     * @param listName document library name
     */
    public MicrosoftGraphProperties {
        if (secretName == null || secretName.isBlank()) {
            secretName = "timesheet-prd-microsoft-graph-credentials";
        }
        if (sharepointSite == null || sharepointSite.isBlank()) {
            sharepointSite = "https://cmacgmgroup.sharepoint.com/sites/CMA-SharedKPIAutomation";
        }
        if (listName == null || listName.isBlank()) {
            listName = "Timesheet";
        }
        clientId = blankToEmpty(clientId);
        clientSecret = blankToEmpty(clientSecret);
        tenantId = blankToEmpty(tenantId);
    }

    /**
     * @return true when tenant, client id and client secret are all present
     */
    public boolean hasCredentials() {
        return !tenantId.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
