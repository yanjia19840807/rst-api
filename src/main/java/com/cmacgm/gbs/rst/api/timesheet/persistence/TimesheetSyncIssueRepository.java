package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Blocking sync issues.
 */
public interface TimesheetSyncIssueRepository extends JpaRepository<TimesheetSyncIssue, UUID> {
}
