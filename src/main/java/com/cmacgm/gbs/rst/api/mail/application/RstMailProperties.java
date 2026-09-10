package com.cmacgm.gbs.rst.api.mail.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Single environment switch for every RST-originated email, plus optional test inbox.
 *
 * @param enabled when false, workflow and Timesheet-failure mail are not sent
 * @param redirectTo when set, every outgoing mail goes to this address instead
 */
@ConfigurationProperties(prefix = "rst.mail")
public record RstMailProperties(boolean enabled, String redirectTo) {

    /**
     * Trims a blank redirect so callers can treat empty as unset.
     *
     * @param enabled outgoing mail switch
     * @param redirectTo optional override inbox
     */
    public RstMailProperties {
        redirectTo = redirectTo == null || redirectTo.isBlank() ? null : redirectTo.trim();
    }

    /**
     * @return true when outgoing mail should be redirected to {@link #redirectTo()}
     */
    public boolean redirectEnabled() {
        return redirectTo != null;
    }
}
