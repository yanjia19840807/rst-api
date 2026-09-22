package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Monthly Toolkit / Dashboard scopes.
 */
public interface TimesheetScopeRepository extends JpaRepository<TimesheetScope, TimesheetScope.Id> {

    /**
     * Scopes owned by a Supervisor occupant.
     *
     * @param ccgid supervisor ccgid
     * @return scope rows
     */
    @Query("""
            select s
            from TimesheetScope s, TimesheetPerson p, TimesheetPersonPositionRole o,
                 TimesheetSyncRun monthly, TimesheetSyncRun daily
            where s.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and p.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and o.id.syncRunId = daily.id
              and o.id.ccgid = p.id.ccgid
              and o.id.positionId = s.id.supervisorPositionId
              and o.id.roleType = 'SUPERVISOR'
              and daily.center = monthly.center
              and p.center = s.id.center
              and s.id.center = monthly.center
              and p.center = daily.center
              and upper(p.id.ccgid) = upper(:ccgid)
            order by s.id.supervisorPositionId, s.id.center, s.id.pl3Code
            """)
    List<TimesheetScope> findActiveBySupervisorCcgid(@Param("ccgid") String ccgid);

    /**
     * Whether a Supervisor occupies a position that owns this PL3.
     *
     * @param ccgid supervisor
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @return true when in scope
     */
    @Query("""
            select count(s) > 0
            from TimesheetScope s, TimesheetPerson p, TimesheetPersonPositionRole o,
                 TimesheetSyncRun monthly, TimesheetSyncRun daily
            where s.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and p.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and o.id.syncRunId = daily.id
              and o.id.ccgid = p.id.ccgid
              and o.id.positionId = s.id.supervisorPositionId
              and o.id.roleType = 'SUPERVISOR'
              and daily.center = monthly.center
              and daily.center = :center
              and monthly.center = :center
              and p.center = :center
              and s.id.center = :center
              and upper(p.id.ccgid) = upper(:ccgid)
              and s.id.supervisorPositionId = :positionId
              and s.id.pl3Code = :pl3Code
            """)
    boolean existsActiveForSupervisor(
            @Param("ccgid") String ccgid,
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code,
            @Param("center") String center);

    /**
     * Whether this Supervisor position × PL3 still exists in ACTIVE Monthly.
     *
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @return true when the scope row is present
     */
    @Query("""
            select count(s) > 0
            from TimesheetScope s, TimesheetSyncRun r
            where s.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and r.center = :center
              and s.id.center = :center
              and s.id.supervisorPositionId = :positionId
              and s.id.pl3Code = :pl3Code
            """)
    boolean existsActiveScope(
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code,
            @Param("center") String center);

    /**
     * Whether the Agent's Daily seat reports to this Supervisor and the
     * Supervisor owns the PL3 in ACTIVE Monthly scope.
     *
     * @param ccgid employee
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @return true when the Agent can use the Toolkit
     */
    @Query("""
            select count(s) > 0
            from TimesheetPerson p, TimesheetPersonPositionRole o, TimesheetPosition pos,
                 TimesheetPositionParent edge, TimesheetScope s,
                 TimesheetSyncRun daily, TimesheetSyncRun monthly
            where p.id.syncRunId = daily.id
              and o.id.syncRunId = daily.id
              and o.id.ccgid = p.id.ccgid
              and pos.id.syncRunId = daily.id
              and pos.id.positionId = o.id.positionId
              and pos.id.roleType = o.id.roleType
              and edge.id.syncRunId = daily.id
              and edge.id.positionId = pos.id.positionId
              and edge.id.roleType = pos.id.roleType
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and s.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
              and pos.id.roleType = 'AGENT'
              and edge.id.parentPositionId = :positionId
              and s.id.supervisorPositionId = edge.id.parentPositionId
              and s.id.pl3Code = :pl3Code
              and daily.center = monthly.center
              and daily.center = :center
              and monthly.center = :center
              and p.center = :center
              and s.id.center = :center
            """)
    boolean existsActiveForAgent(
            @Param("ccgid") String ccgid,
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code,
            @Param("center") String center);

    /**
     * Dashboard obligations: Center × Supervisor position × PL3.
     *
     * @return scope rows
     */
    @Query("""
            select s
            from TimesheetScope s, TimesheetSyncRun r
            where s.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and s.id.center = r.center
            order by s.id.center, s.id.supervisorPositionId, s.id.pl3Code
            """)
    List<TimesheetScope> findActiveDashboardObligations();

    /**
     * Distinct GBS Domains present in this Center in the ACTIVE Monthly snapshot.
     *
     * @param center GBS center
     * @return domain names
     */
    @Query("""
            select distinct s.domain
            from TimesheetScope s, TimesheetSyncRun r
            where s.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and r.center = :center
              and s.id.center = :center
            order by s.domain
            """)
    List<String> findActiveDomainsByCenter(@Param("center") String center);

    /**
     * ACTIVE Monthly scopes for the Timesheet Sync browser.
     *
     * @param center exact center; blank matches all
     * @param supervisor Supervisor position id or occupant name; blank matches all
     * @param pl3Code PL3 code or name fragment; blank matches all
     * @param pageable page
     * @return scopes
     */
    @Query(
            value = """
                    select s
                    from TimesheetScope s, TimesheetSyncRun r
                    where s.id.syncRunId = r.id
                      and r.kind = 'MONTHLY'
                      and r.status = 'ACTIVE'
                      and s.id.center = r.center
                      and (:center = '' or s.id.center = :center)
                      and (:supervisor = ''
                           or lower(s.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat,
                                     TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = r.center
                                  and occupant.center = s.id.center
                                  and seat.id.positionId = s.id.supervisorPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(s.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or lower(coalesce(s.pl3Name, '')) like lower(concat('%', :pl3Code, '%')))
                    order by s.id.center, s.id.supervisorPositionId, s.id.pl3Code
                    """,
            countQuery = """
                    select count(s)
                    from TimesheetScope s, TimesheetSyncRun r
                    where s.id.syncRunId = r.id
                      and r.kind = 'MONTHLY'
                      and r.status = 'ACTIVE'
                      and s.id.center = r.center
                      and (:center = '' or s.id.center = :center)
                      and (:supervisor = ''
                           or lower(s.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat,
                                     TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = r.center
                                  and occupant.center = s.id.center
                                  and seat.id.positionId = s.id.supervisorPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(s.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or lower(coalesce(s.pl3Name, '')) like lower(concat('%', :pl3Code, '%')))
                    """)
    Page<TimesheetScope> searchActive(
            @Param("center") String center,
            @Param("supervisor") String supervisor,
            @Param("pl3Code") String pl3Code,
            Pageable pageable);

    /**
     * ACTIVE Monthly scopes owned by the given Supervisor positions.
     *
     * @param supervisorPositionIds supervisor seats
     * @return scopes
     */
    @Query("""
            select s
            from TimesheetScope s, TimesheetSyncRun r
            where s.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and s.id.center = r.center
              and s.id.supervisorPositionId in :supervisorPositionIds
            """)
    List<TimesheetScope> findActiveBySupervisorPositionIdIn(
            @Param("supervisorPositionIds") Collection<String> supervisorPositionIds);

    /**
     * Distinct centers in the ACTIVE Monthly scope snapshot.
     *
     * @return centers
     */
    @Query("""
            select distinct s.id.center
            from TimesheetScope s, TimesheetSyncRun r
            where s.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and s.id.center = r.center
              and s.id.center is not null
              and s.id.center <> ''
            order by s.id.center
            """)
    List<String> findActiveCenters();

    /**
     * Distinct domains in the ACTIVE Monthly scope snapshot.
     *
     * @return domains
     */
    @Query("""
            select distinct s.domain
            from TimesheetScope s, TimesheetSyncRun r
            where s.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and s.id.center = r.center
              and s.domain is not null
              and s.domain <> ''
            order by s.domain
            """)
    List<String> findActiveDomains();

    /**
     * Drops Monthly scope rows for other runs of this kind and Center.
     *
     * @param kind MONTHLY
     * @param center GBS center
     * @param keepRunId ACTIVE Monthly run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from TimesheetScope s
            where s.id.syncRunId in (
                select r.id from TimesheetSyncRun r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
