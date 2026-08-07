package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for Timesheet sync run headers.
 */
public interface TimesheetSyncRunRepository extends JpaRepository<TimesheetSyncRun, UUID> {

    /**
     * Finds the current ACTIVE sync run, if any.
     *
     * @param status status value
     * @return optional ACTIVE run
     */
    Optional<TimesheetSyncRun> findByStatus(String status);

    /**
     * Returns the highest attempt number for a sync date.
     *
     * @param syncDate business sync date
     * @return max attempt or null when none exist
     */
    @Query("select max(r.attemptNo) from TimesheetSyncRun r where r.syncDate = :syncDate")
    Short findMaxAttemptNo(@Param("syncDate") LocalDate syncDate);
}
