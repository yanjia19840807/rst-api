package com.cmacgm.gbs.rst.api.mail.application;

import com.cmacgm.gbs.rst.api.workforce.WorkforceRead;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import org.springframework.stereotype.Service;

/**
 * Reads emp_email from the active Daily Timesheet person row.
 */
@Service
public class TimesheetMailAddressLookup implements MailAddressLookup {

    private final WorkforceRead workforce;

    /**
     * @param timesheet active Daily people
     */
    public TimesheetMailAddressLookup(WorkforceRead workforce) {
        this.workforce = workforce;
    }

    @Override
    public String emailOf(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return null;
        }
        return workforce.findActivePerson(ccgid.trim())
                .map(OrgPerson::getEmail)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
    }
}
