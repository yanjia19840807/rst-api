package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;
import java.util.Optional;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Daily position occupancy.
 */
public interface TimesheetOccupancyRepository
        extends JpaRepository<TimesheetOccupancy, TimesheetOccupancy.Id> {

    /**
     * Positions currently occupied by this person for a role.
     *
     * @param ccgid occupant
     * @param roleType SUPERVISOR / SR_MANAGER / DOMAIN_HEAD
     * @return position ids
     */
    @Query("""
            select o.id.positionId
            from TimesheetOccupancy o, TimesheetPosition p, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and p.id.syncRunId = r.id
              and p.id.positionId = o.id.positionId
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(o.empCcgid) = upper(:ccgid)
              and p.roleType = :roleType
            """)
    List<String> findActivePositionIdsByCcgidAndRole(
            @Param("ccgid") String ccgid, @Param("roleType") String roleType);

    /**
     * Positions currently occupied by this person.
     *
     * @param ccgid occupant
     * @return position ids
     */
    @Query("""
            select o.id.positionId
            from TimesheetOccupancy o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(o.empCcgid) = upper(:ccgid)
            """)
    List<String> findActivePositionIdsByCcgid(@Param("ccgid") String ccgid);

    /**
     * Current occupant of a position.
     *
     * @param positionId position
     * @return occupancy when present
     */
    @Query("""
            select o
            from TimesheetOccupancy o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and o.id.positionId = :positionId
            """)
    Optional<TimesheetOccupancy> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * Whether this person occupies the Supervisor position.
     *
     * @param ccgid occupant
     * @param positionId supervisor position
     * @return true when occupied
     */
    @Query("""
            select count(o) > 0
            from TimesheetOccupancy o, TimesheetPosition p, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and p.id.syncRunId = r.id
              and p.id.positionId = o.id.positionId
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(o.empCcgid) = upper(:ccgid)
              and o.id.positionId = :positionId
              and p.roleType = 'SUPERVISOR'
            """)
    boolean existsActiveSupervisor(
            @Param("ccgid") String ccgid, @Param("positionId") String positionId);
}
