package com.cmacgm.gbs.rst.api.mail.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Workflow mail switch and optional test inbox redirect.
 *
 * @param workflowEnabled when false, approval / outcome mail is not sent
 * @param redirectTo when set, every workflow mail goes to this address instead
 */
@ConfigurationProperties(prefix = "rst.mail")
public record RstMailProperties(boolean workflowEnabled, String redirectTo) {

    /**
     * Trims a blank redirect so callers can treat empty as unset.
     *
     * @param workflowEnabled workflow mail switch
     * @param redirectTo optional override inbox
     */
    public RstMailProperties {
        redirectTo = redirectTo == null || redirectTo.isBlank() ? null : redirectTo.trim();
    }

    /**
     * @return true when workflow mail should be redirected to {@link #redirectTo()}
     */
    public boolean redirectEnabled() {
        return redirectTo != null;
    }
}
