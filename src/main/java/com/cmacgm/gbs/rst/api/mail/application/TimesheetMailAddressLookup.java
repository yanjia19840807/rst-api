package com.cmacgm.gbs.rst.api.mail.application;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import org.springframework.stereotype.Service;

/**
 * Reads emp_email from the active Daily Timesheet person row.
 */
@Service
public class TimesheetMailAddressLookup implements MailAddressLookup {

    private final TimesheetReadService timesheet;

    /**
     * @param timesheet active Daily people
     */
    public TimesheetMailAddressLookup(TimesheetReadService timesheet) {
        this.timesheet = timesheet;
    }

    @Override
    public String emailOf(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return null;
        }
        return timesheet.findActivePerson(ccgid.trim())
                .map(TimesheetPerson::getEmail)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
    }
}
