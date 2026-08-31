package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Daily position tree.
 */
public interface TimesheetPositionRepository
        extends JpaRepository<TimesheetPosition, TimesheetPosition.Id> {

    /**
     * Finds an ACTIVE Daily position.
     *
     * @param positionId position
     * @return position when present
     */
    @Query("""
            select p
            from TimesheetPosition p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.id.positionId = :positionId
            """)
    Optional<TimesheetPosition> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * ACTIVE Daily positions for the Timesheet Sync browser.
     *
     * @param roleType PRODUCTION / SUPERVISOR / SR_MANAGER / DOMAIN_HEAD; blank matches all
     * @param q position or parent id fragment; blank matches all
     * @param pageable page
     * @return positions
     */
    @Query(
            value = """
                    select p
                    from TimesheetPosition p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:roleType = '' or p.roleType = :roleType)
                      and (:q = ''
                           or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.parentPositionId, '')) like lower(concat('%', :q, '%')))
                    order by p.roleType, p.id.positionId
                    """,
            countQuery = """
                    select count(p)
                    from TimesheetPosition p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:roleType = '' or p.roleType = :roleType)
                      and (:q = ''
                           or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.parentPositionId, '')) like lower(concat('%', :q, '%')))
                    """)
    Page<TimesheetPosition> searchActive(
            @Param("roleType") String roleType, @Param("q") String q, Pageable pageable);

    /**
     * Drops Daily position rows that are not the kept snapshot.
     *
     * @param keepRunId ACTIVE Daily run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TimesheetPosition p where p.id.syncRunId <> :keepRunId")
    int deleteBySyncRunIdNot(@Param("keepRunId") UUID keepRunId);
}
