package com.cmacgm.gbs.rst.api.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Microsoft Graph client-credentials settings. Production injects client id and secret from
 * the named Azure / Kubernetes secret; local runs use {@code MS_GRAPH_*} environment variables.
 *
 * @param enabled when false, Graph calls fail fast without contacting Microsoft
 * @param secretName ops secret name that holds Graph credentials
 * @param tenantId Azure AD tenant used for the token request
 * @param authUri Microsoft identity token URL
 * @param clientId application (client) id
 * @param clientSecret application client secret
 * @param scope OAuth scope, typically {@code https://graph.microsoft.com/.default}
 * @param graphHost Graph REST host, typically {@code https://graph.microsoft.com/v1.0}
 * @param sharepointSite SharePoint site web URL
 * @param listName document library / list name on that site
 * @param envPrefix SharePoint folder prefix such as {@code 3.Production} or {@code 1.SIT}
 */
@ConfigurationProperties(prefix = "microsoft.graph")
public record MicrosoftGraphProperties(
        boolean enabled,
        String secretName,
        String tenantId,
        String authUri,
        String clientId,
        String clientSecret,
        String scope,
        String graphHost,
        String sharepointSite,
        String listName,
        String envPrefix) {

    /**
     * Fills Graph defaults when a field is blank.
     *
     * @param enabled Graph enable flag
     * @param secretName credential secret name
     * @param tenantId Azure tenant
     * @param authUri token endpoint URL
     * @param clientId application id
     * @param clientSecret application secret
     * @param scope OAuth scope
     * @param graphHost Graph host
     * @param sharepointSite SharePoint site URL
     * @param listName document library name
     * @param envPrefix folder prefix
     */
    public MicrosoftGraphProperties {
        if (secretName == null || secretName.isBlank()) {
            secretName = "timesheet-prd-microsoft-graph-credentials";
        }
        if (scope == null || scope.isBlank()) {
            scope = "https://graph.microsoft.com/.default";
        }
        if (graphHost == null || graphHost.isBlank()) {
            graphHost = "https://graph.microsoft.com/v1.0";
        }
        if (sharepointSite == null || sharepointSite.isBlank()) {
            sharepointSite = "https://cmacgmgroup.sharepoint.com/sites/CMA-SharedKPIAutomation";
        }
        if (listName == null || listName.isBlank()) {
            listName = "Timesheet";
        }
        if (envPrefix == null || envPrefix.isBlank()) {
            envPrefix = "3.Production";
        }
        clientId = blankToEmpty(clientId);
        clientSecret = blankToEmpty(clientSecret);
        tenantId = blankToEmpty(tenantId);
        if (authUri == null || authUri.isBlank()) {
            authUri = tenantId.isBlank()
                    ? ""
                    : "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        } else {
            authUri = authUri.trim();
        }
    }

    /**
     * @return true when tenant, client id and client secret are all present
     */
    public boolean hasCredentials() {
        return !tenantId.isBlank() && !authUri.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
