package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPersonPositionRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Daily occupancies.
 */
public interface TimesheetPersonPositionRoleRepository
        extends JpaRepository<TimesheetPersonPositionRole, TimesheetPersonPositionRole.Id> {

    /**
     * Occupancies for one ACTIVE Daily person.
     *
     * @param ccgid identity
     * @return seats
     */
    @Query("""
            select o
            from TimesheetPersonPositionRole o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(o.id.ccgid) = upper(:ccgid)
            """)
    List<TimesheetPersonPositionRole> findActiveByCcgid(@Param("ccgid") String ccgid);

    /**
     * Occupancies for ACTIVE Daily people.
     *
     * @param ccgids identities
     * @return seats
     */
    @Query("""
            select o
            from TimesheetPersonPositionRole o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(o.id.ccgid) in :ccgids
            """)
    List<TimesheetPersonPositionRole> findActiveByCcgidIn(@Param("ccgids") Collection<String> ccgids);

    /**
     * Occupancies of a position in the ACTIVE Daily snapshot.
     *
     * @param positionId Timesheet position
     * @return seats
     */
    @Query("""
            select o
            from TimesheetPersonPositionRole o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and o.id.positionId = :positionId
            order by o.id.ccgid, o.id.roleType
            """)
    List<TimesheetPersonPositionRole> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * Occupancies of the given positions.
     *
     * @param positionIds Timesheet positions
     * @return seats
     */
    @Query("""
            select o
            from TimesheetPersonPositionRole o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and o.id.positionId in :positionIds
            order by o.id.positionId, o.id.ccgid, o.id.roleType
            """)
    List<TimesheetPersonPositionRole> findActiveByPositionIdIn(
            @Param("positionIds") Collection<String> positionIds);

    /**
     * Position occupied by this person for a role.
     *
     * @param ccgid occupant
     * @param roleType AGENT / SUPERVISOR / SR_MANAGER
     * @return position id
     */
    @Query("""
            select o.id.positionId
            from TimesheetPersonPositionRole o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(o.id.ccgid) = upper(:ccgid)
              and o.id.roleType = :roleType
            """)
    List<String> findActivePositionIdsByCcgidAndRole(
            @Param("ccgid") String ccgid, @Param("roleType") String roleType);

    /**
     * Whether this position belongs to the Center.
     *
     * @param positionId Timesheet position
     * @param center GBS center
     * @return true when present
     */
    @Query("""
            select count(o) > 0
            from TimesheetPersonPositionRole o, TimesheetSyncRun r
            where o.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and r.center = :center
              and o.id.positionId = :positionId
            """)
    boolean existsActivePositionInCenter(
            @Param("positionId") String positionId, @Param("center") String center);

    /**
     * ACTIVE Daily occupancies.
     *
     * @param center exact run center; blank matches all
     * @param q name / CCGID / position / role; blank matches all
     * @param pageable page
     * @return seats
     */
    @Query(
            value = """
                    select o.id.ccgid as ccgid,
                           p.name as name,
                           o.id.positionId as positionId,
                           o.id.roleType as roleType,
                           r.center as center
                    from TimesheetPersonPositionRole o, TimesheetPerson p, TimesheetSyncRun r
                    where o.id.syncRunId = r.id
                      and p.id.syncRunId = r.id
                      and p.id.ccgid = o.id.ccgid
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(o.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(o.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(o.id.roleType) like lower(concat('%', :q, '%')))
                    order by r.center, o.id.ccgid,
                             case o.id.roleType
                                 when 'AGENT' then 0
                                 when 'SUPERVISOR' then 1
                                 else 2
                             end
                    """,
            countQuery = """
                    select count(o)
                    from TimesheetPersonPositionRole o, TimesheetPerson p, TimesheetSyncRun r
                    where o.id.syncRunId = r.id
                      and p.id.syncRunId = r.id
                      and p.id.ccgid = o.id.ccgid
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(o.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(o.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(o.id.roleType) like lower(concat('%', :q, '%')))
                    """)
    Page<OccupancyRow> searchActive(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * One occupancy with the person name and run Center.
     */
    interface OccupancyRow {
        String getCcgid();

        String getName();

        String getPositionId();

        String getRoleType();

        String getCenter();
    }

    /**
     * Drops occupancy rows for other runs of this kind and Center.
     *
     * @param kind DAILY
     * @param center GBS center
     * @param keepRunId ACTIVE Daily run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from TimesheetPersonPositionRole o
            where o.id.syncRunId in (
                select r.id from TimesheetSyncRun r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
