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
            from TimesheetScope s, TimesheetPerson p, TimesheetSyncRun monthly, TimesheetSyncRun daily
            where s.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and p.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and p.positionId = s.id.supervisorPositionId
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
            from TimesheetScope s, TimesheetPerson p, TimesheetSyncRun monthly, TimesheetSyncRun daily
            where s.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and p.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and p.positionId = s.id.supervisorPositionId
              and upper(p.id.ccgid) = upper(:ccgid)
              and s.id.supervisorPositionId = :positionId
              and s.id.pl3Code = :pl3Code
            """)
    boolean existsActiveForSupervisor(
            @Param("ccgid") String ccgid,
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code);

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
                      and (:center = '' or s.id.center = :center)
                      and (:supervisor = ''
                           or lower(s.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = s.id.supervisorPositionId
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
                      and (:center = '' or s.id.center = :center)
                      and (:supervisor = ''
                           or lower(s.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = s.id.supervisorPositionId
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
              and s.domain is not null
              and s.domain <> ''
            order by s.domain
            """)
    List<String> findActiveDomains();

    /**
     * Drops Monthly scope rows that are not the kept snapshot.
     *
     * @param keepRunId ACTIVE Monthly run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TimesheetScope s where s.id.syncRunId <> :keepRunId")
    int deleteBySyncRunIdNot(@Param("keepRunId") UUID keepRunId);
}
