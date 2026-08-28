package com.cmacgm.gbs.rst.api.mail.application;

/**
 * Resolves the delivery address for a CCGID from Timesheet, not from login.
 */
@FunctionalInterface
public interface MailAddressLookup {

    /**
     * @param ccgid recipient
     * @return Timesheet emp_email, or null
     */
    String emailOf(String ccgid);
}
