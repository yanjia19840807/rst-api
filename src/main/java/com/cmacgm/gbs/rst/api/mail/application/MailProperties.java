package com.cmacgm.gbs.rst.api.mail.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outgoing mail switch, optional test inbox, and Graph send-as mailbox.
 *
 * @param enabled when false, workflow and Timesheet-failure mail are not sent
 * @param redirectTo when set, every outgoing mail goes to this address instead
 * @param from Graph mailbox that sends the message
 */
@ConfigurationProperties(prefix = "mail")
public record MailProperties(boolean enabled, String redirectTo, String from) {

    private static final String DEFAULT_FROM = "GBS.TIMESHEET@cma-cgm.com";

    /**
     * Trims a blank redirect so callers can treat empty as unset.
     *
     * @param enabled outgoing mail switch
     * @param redirectTo optional override inbox
     * @param from send-as mailbox
     */
    public MailProperties {
        redirectTo = redirectTo == null || redirectTo.isBlank() ? null : redirectTo.trim();
        from = from == null || from.isBlank() ? DEFAULT_FROM : from.trim();
    }

    /**
     * @return true when outgoing mail should be redirected to {@link #redirectTo()}
     */
    public boolean redirectEnabled() {
        return redirectTo != null;
    }
}
