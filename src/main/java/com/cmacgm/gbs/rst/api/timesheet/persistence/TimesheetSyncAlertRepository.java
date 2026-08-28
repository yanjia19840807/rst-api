package com.cmacgm.gbs.rst.api.timesheet.persistence;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncAlert;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the single Timesheet sync alert row.
 */
public interface TimesheetSyncAlertRepository extends JpaRepository<TimesheetSyncAlert, Short> {
}
