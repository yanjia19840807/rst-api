package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPositionParent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Parent edges of Daily position nodes.
 */
public interface TimesheetPositionParentRepository
        extends JpaRepository<TimesheetPositionParent, TimesheetPositionParent.Id> {

    /**
     * ACTIVE Daily parent edges of a position, any role.
     *
     * @param positionId child position
     * @return edges
     */
    @Query("""
            select e
            from TimesheetPositionParent e, TimesheetSyncRun r
            where e.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and e.id.positionId = :positionId
            order by e.id.roleType, e.id.parentPositionId, e.id.parentRoleType
            """)
    List<TimesheetPositionParent> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * ACTIVE Daily parent edges for the given child positions.
     *
     * @param center exact run center; blank matches all
     * @param positionIds child positions
     * @return edges
     */
    @Query("""
            select e
            from TimesheetPositionParent e, TimesheetSyncRun r
            where e.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and (:center = '' or r.center = :center)
              and e.id.positionId in :positionIds
            order by e.id.positionId, e.id.roleType, e.id.parentPositionId, e.id.parentRoleType
            """)
    List<TimesheetPositionParent> findActiveByPositionIdIn(
            @Param("center") String center, @Param("positionIds") Collection<String> positionIds);

    /**
     * Drops parent edges for other runs of this kind and Center.
     *
     * @param kind DAILY
     * @param center GBS center
     * @param keepRunId ACTIVE Daily run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from TimesheetPositionParent e
            where e.id.syncRunId in (
                select r.id from TimesheetSyncRun r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
