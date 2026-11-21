package com.cmacgm.gbs.rst.api.timesheet.persistence;

import com.cmacgm.gbs.rst.api.timesheet.domain.SourceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the single Timesheet sync alert row.
 */
public interface SourceAlertRepository extends JpaRepository<SourceAlert, Short> {
}
