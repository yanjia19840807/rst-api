package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for Timesheet sync run headers.
 */
public interface TimesheetSyncRunRepository
        extends JpaRepository<TimesheetSyncRun, UUID>, JpaSpecificationExecutor<TimesheetSyncRun> {

    /**
     * Finds a run by kind and status.
     *
     * @param kind DAILY or MONTHLY
     * @param status status
     * @return optional run
     */
    Optional<TimesheetSyncRun> findByKindAndStatus(String kind, String status);

    /**
     * Highest attempt for a kind and business date.
     *
     * @param kind DAILY or MONTHLY
     * @param syncDate business date
     * @return max attempt or null
     */
    @Query("""
            select max(r.attemptNo)
            from TimesheetSyncRun r
            where r.kind = :kind and r.syncDate = :syncDate
            """)
    Short findMaxAttemptNo(@Param("kind") String kind, @Param("syncDate") LocalDate syncDate);

    /**
     * Latest ACTIVE run for a SharePoint file identity.
     *
     * @param kind DAILY or MONTHLY
     * @param driveItemId Graph item id
     * @param etag Graph etag
     * @return optional ACTIVE run
     */
    Optional<TimesheetSyncRun> findByKindAndStatusAndSourceDriveItemIdAndSourceEtag(
            String kind, String status, String driveItemId, String etag);

    /**
     * Archives every other ACTIVE run of this kind so the unique index can
     * accept the incoming snapshot.
     *
     * @param kind DAILY or MONTHLY
     * @param runId run that will become ACTIVE
     * @param completedAt archive time when the previous row has none
     * @return archived rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TimesheetSyncRun r
            set r.status = 'ARCHIVED',
                r.completedAt = coalesce(r.completedAt, :completedAt)
            where r.kind = :kind and r.status = 'ACTIVE' and r.id <> :runId
            """)
    int archiveOtherActive(
            @Param("kind") String kind, @Param("runId") UUID runId, @Param("completedAt") Instant completedAt);
}
