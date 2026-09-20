package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
     * Finds runs by kind and status. There may be one ACTIVE per Center.
     *
     * @param kind DAILY or MONTHLY
     * @param status status
     * @return matching runs
     */
    List<TimesheetSyncRun> findByKindAndStatus(String kind, String status);

    /**
     * Finds the run for one Center.
     *
     * @param kind DAILY or MONTHLY
     * @param status status
     * @param center GBS center
     * @return optional run
     */
    Optional<TimesheetSyncRun> findByKindAndStatusAndCenter(String kind, String status, String center);

    /**
     * Highest attempt for a kind, Center, and business date.
     *
     * @param kind DAILY or MONTHLY
     * @param center GBS center
     * @param syncDate business date
     * @return max attempt or null
     */
    @Query("""
            select max(r.attemptNo)
            from TimesheetSyncRun r
            where r.kind = :kind and r.center = :center and r.syncDate = :syncDate
            """)
    Short findMaxAttemptNo(
            @Param("kind") String kind, @Param("center") String center, @Param("syncDate") LocalDate syncDate);

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
     * Archives every other ACTIVE run of this kind and Center.
     *
     * @param kind DAILY or MONTHLY
     * @param center GBS center
     * @param runId run that will become ACTIVE
     * @param completedAt archive time when the previous row has none
     * @return archived rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TimesheetSyncRun r
            set r.status = 'ARCHIVED',
                r.completedAt = coalesce(r.completedAt, :completedAt)
            where r.kind = :kind
              and r.center = :center
              and r.status = 'ACTIVE'
              and r.id <> :runId
            """)
    int archiveOtherActive(
            @Param("kind") String kind,
            @Param("center") String center,
            @Param("runId") UUID runId,
            @Param("completedAt") Instant completedAt);
}
