package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Monthly Agent-seat assignments.
 */
public interface TimesheetAssignmentRepository
        extends JpaRepository<TimesheetAssignment, TimesheetAssignment.Id> {

    /**
     * Whether the Agent's current seat is assigned to this Supervisor × PL3.
     *
     * @param ccgid employee
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @return true when assigned
     */
    @Query("""
            select count(a) > 0
            from TimesheetAssignment a, TimesheetPerson p, TimesheetSyncRun monthly, TimesheetSyncRun daily
            where a.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and p.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
              and p.positionId = a.id.empPositionId
              and a.id.supervisorPositionId = :positionId
              and a.id.pl3Code = :pl3Code
            """)
    boolean existsActiveForAgent(
            @Param("ccgid") String ccgid,
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code);

    /**
     * Distinct Agent seats under a Supervisor occupant.
     *
     * @param supervisorCcgid supervisor
     * @return assignments
     */
    @Query("""
            select a
            from TimesheetAssignment a, TimesheetPerson p, TimesheetSyncRun monthly, TimesheetSyncRun daily
            where a.id.syncRunId = monthly.id
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and p.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and p.positionId = a.id.supervisorPositionId
              and upper(p.id.ccgid) = upper(:supervisorCcgid)
            """)
    List<TimesheetAssignment> findActiveBySupervisorCcgid(
            @Param("supervisorCcgid") String supervisorCcgid);

    /**
     * ACTIVE Monthly assignments for the Timesheet Sync browser.
     *
     * @param center exact center; blank matches all
     * @param agent Agent position id or occupant name; blank matches all
     * @param supervisor Supervisor position id or occupant name; blank matches all
     * @param pl3Code PL3 code or name fragment; blank matches all
     * @param pageable page
     * @return assignments
     */
    @Query(
            value = """
                    select a
                    from TimesheetAssignment a, TimesheetSyncRun r
                    where a.id.syncRunId = r.id
                      and r.kind = 'MONTHLY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or a.id.center = :center)
                      and (:agent = ''
                           or lower(a.id.empPositionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = a.id.empPositionId
                                  and lower(occupant.name) like lower(concat('%', :agent, '%'))))
                      and (:supervisor = ''
                           or lower(a.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = a.id.supervisorPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(a.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or exists (
                                select 1
                                from TimesheetScope scope
                                where scope.id.syncRunId = a.id.syncRunId
                                  and scope.id.supervisorPositionId = a.id.supervisorPositionId
                                  and scope.id.pl3Code = a.id.pl3Code
                                  and scope.id.center = a.id.center
                                  and lower(coalesce(scope.pl3Name, '')) like lower(concat('%', :pl3Code, '%'))))
                    order by a.id.empPositionId, a.id.supervisorPositionId, a.id.pl3Code, a.id.center
                    """,
            countQuery = """
                    select count(a)
                    from TimesheetAssignment a, TimesheetSyncRun r
                    where a.id.syncRunId = r.id
                      and r.kind = 'MONTHLY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or a.id.center = :center)
                      and (:agent = ''
                           or lower(a.id.empPositionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = a.id.empPositionId
                                  and lower(occupant.name) like lower(concat('%', :agent, '%'))))
                      and (:supervisor = ''
                           or lower(a.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = a.id.supervisorPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(a.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or exists (
                                select 1
                                from TimesheetScope scope
                                where scope.id.syncRunId = a.id.syncRunId
                                  and scope.id.supervisorPositionId = a.id.supervisorPositionId
                                  and scope.id.pl3Code = a.id.pl3Code
                                  and scope.id.center = a.id.center
                                  and lower(coalesce(scope.pl3Name, '')) like lower(concat('%', :pl3Code, '%'))))
                    """)
    Page<TimesheetAssignment> searchActive(
            @Param("center") String center,
            @Param("agent") String agent,
            @Param("supervisor") String supervisor,
            @Param("pl3Code") String pl3Code,
            Pageable pageable);

    /**
     * Drops Monthly assignment rows that are not the kept snapshot.
     *
     * @param keepRunId ACTIVE Monthly run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TimesheetAssignment a where a.id.syncRunId <> :keepRunId")
    int deleteBySyncRunIdNot(@Param("keepRunId") UUID keepRunId);
}
