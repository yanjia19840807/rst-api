package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import org.springframework.data.jpa.repository.JpaRepository;
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
            from TimesheetScope s, TimesheetOccupancy o, TimesheetSyncRun monthly, TimesheetSyncRun daily
            where s.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and o.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and o.id.positionId = s.id.supervisorPositionId
              and upper(o.empCcgid) = upper(:ccgid)
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
            from TimesheetScope s, TimesheetOccupancy o, TimesheetSyncRun monthly, TimesheetSyncRun daily
            where s.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and o.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and o.id.positionId = s.id.supervisorPositionId
              and upper(o.empCcgid) = upper(:ccgid)
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
}
