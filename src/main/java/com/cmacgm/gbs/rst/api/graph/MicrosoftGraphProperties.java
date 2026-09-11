package com.cmacgm.gbs.rst.api.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Microsoft Graph client-credentials. SharePoint locations live on the feature
 * that owns them ({@code timesheet.sharepoint}, {@code process.sharepoint}).
 *
 * @param secretName ops secret name that holds Graph credentials
 * @param tenantId Azure AD tenant
 * @param clientId application (client) id
 * @param clientSecret application client secret
 */
@ConfigurationProperties(prefix = "microsoft.graph")
public record MicrosoftGraphProperties(
        String secretName,
        String tenantId,
        String clientId,
        String clientSecret) {

    /**
     * Fills Graph defaults when a field is blank.
     *
     * @param secretName credential secret name
     * @param tenantId Azure tenant
     * @param clientId application id
     * @param clientSecret application secret
     */
    public MicrosoftGraphProperties {
        if (secretName == null || secretName.isBlank()) {
            secretName = "timesheet-prd-microsoft-graph-credentials";
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
