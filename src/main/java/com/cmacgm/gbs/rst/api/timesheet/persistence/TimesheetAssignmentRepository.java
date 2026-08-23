package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Daily employee assignments.
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
              and r.kind = 'DAILY'
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
            from TimesheetAssignment a, TimesheetOccupancy o, TimesheetSyncRun r
            where a.id.syncRunId = r.id
              and o.id.syncRunId = r.id
              and o.id.positionId = a.id.supervisorPositionId
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(o.empCcgid) = upper(:supervisorCcgid)
            """)
    List<TimesheetAssignment> findActiveBySupervisorCcgid(
            @Param("supervisorCcgid") String supervisorCcgid);
}
