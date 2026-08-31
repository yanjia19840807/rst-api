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
 * Monthly employee assignments.
 */
public interface TimesheetAssignmentRepository
        extends JpaRepository<TimesheetAssignment, TimesheetAssignment.Id> {

    /**
     * Whether an Agent can use a Toolkit scope.
     *
     * @param ccgid employee
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @return true when assigned
     */
    @Query("""
            select count(a) > 0
            from TimesheetAssignment a, TimesheetSyncRun r
            where a.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and upper(a.id.empCcgid) = upper(:ccgid)
              and a.id.supervisorPositionId = :positionId
              and a.id.pl3Code = :pl3Code
            """)
    boolean existsActiveForAgent(
            @Param("ccgid") String ccgid,
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code);

    /**
     * Distinct agents under a Supervisor occupant.
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
     * @param supervisorPositionId exact supervisor position; blank matches all
     * @param pl3Code exact PL3; blank matches all
     * @param q CCGID / emp id fragment; blank matches all
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
                      and (:supervisorPositionId = '' or a.id.supervisorPositionId = :supervisorPositionId)
                      and (:pl3Code = '' or a.id.pl3Code = :pl3Code)
                      and (:q = ''
                           or lower(a.id.empCcgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(a.empId, '')) like lower(concat('%', :q, '%')))
                    order by a.id.empCcgid, a.id.supervisorPositionId, a.id.pl3Code
                    """,
            countQuery = """
                    select count(a)
                    from TimesheetAssignment a, TimesheetSyncRun r
                    where a.id.syncRunId = r.id
                      and r.kind = 'MONTHLY'
                      and r.status = 'ACTIVE'
                      and (:supervisorPositionId = '' or a.id.supervisorPositionId = :supervisorPositionId)
                      and (:pl3Code = '' or a.id.pl3Code = :pl3Code)
                      and (:q = ''
                           or lower(a.id.empCcgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(a.empId, '')) like lower(concat('%', :q, '%')))
                    """)
    Page<TimesheetAssignment> searchActive(
            @Param("supervisorPositionId") String supervisorPositionId,
            @Param("pl3Code") String pl3Code,
            @Param("q") String q,
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
