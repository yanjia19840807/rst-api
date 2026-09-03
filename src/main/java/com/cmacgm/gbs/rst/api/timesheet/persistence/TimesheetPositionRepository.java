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
     * ACTIVE Daily AGENT seats with Supervisor and SR Manager parents.
     *
     * @param center exact Agent-seat center; blank matches all
     * @param q position id or occupant name on Agent, Supervisor or SR Manager; blank matches all
     * @param pageable page
     * @return one row per AGENT position
     */
    @Query(
            value = """
                    select agent.id.positionId as agentPositionId,
                           agent.parentPositionId as supervisorPositionId,
                           supervisor.parentPositionId as srManagerPositionId,
                           agent.center as center
                    from TimesheetPosition agent
                    join TimesheetSyncRun r on agent.id.syncRunId = r.id
                    left join TimesheetPosition supervisor
                      on supervisor.id.syncRunId = agent.id.syncRunId
                     and supervisor.id.positionId = agent.parentPositionId
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.roleType = 'AGENT'
                      and (:center = '' or agent.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(agent.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisor.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId in (
                                        agent.id.positionId,
                                        agent.parentPositionId,
                                        supervisor.parentPositionId)
                                  and lower(occupant.name) like lower(concat('%', :q, '%'))))
                    order by agent.center, agent.id.positionId
                    """,
            countQuery = """
                    select count(agent)
                    from TimesheetPosition agent
                    join TimesheetSyncRun r on agent.id.syncRunId = r.id
                    left join TimesheetPosition supervisor
                      on supervisor.id.syncRunId = agent.id.syncRunId
                     and supervisor.id.positionId = agent.parentPositionId
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.roleType = 'AGENT'
                      and (:center = '' or agent.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(agent.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisor.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId in (
                                        agent.id.positionId,
                                        agent.parentPositionId,
                                        supervisor.parentPositionId)
                                  and lower(occupant.name) like lower(concat('%', :q, '%'))))
                    """)
    Page<PositionChain> searchActiveChains(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * Derived Agent × Supervisor × PL3 rows: Daily AGENT parent plus Monthly
     * scope owned by that Supervisor.
     *
     * @param center exact scope center; blank matches all
     * @param agent Agent position id or occupant name; blank matches all
     * @param supervisor Supervisor position id or occupant name; blank matches all
     * @param pl3Code PL3 code or name fragment; blank matches all
     * @param pageable page
     * @return derived assignments
     */
    @Query(
            value = """
                    select agent.id.positionId as agentPositionId,
                           agent.parentPositionId as supervisorPositionId,
                           scope.id.pl3Code as pl3Code,
                           scope.pl3Name as pl3Name,
                           scope.id.center as center
                    from TimesheetPosition agent, TimesheetScope scope,
                         TimesheetSyncRun daily, TimesheetSyncRun monthly
                    where agent.id.syncRunId = daily.id
                      and daily.kind = 'DAILY'
                      and daily.status = 'ACTIVE'
                      and scope.id.syncRunId = monthly.id
                      and monthly.kind = 'MONTHLY'
                      and monthly.status = 'ACTIVE'
                      and agent.roleType = 'AGENT'
                      and agent.parentPositionId = scope.id.supervisorPositionId
                      and (:center = '' or scope.id.center = :center)
                      and (:agent = ''
                           or lower(agent.id.positionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = agent.id.positionId
                                  and lower(occupant.name) like lower(concat('%', :agent, '%'))))
                      and (:supervisor = ''
                           or lower(agent.parentPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = agent.parentPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(scope.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or lower(coalesce(scope.pl3Name, '')) like lower(concat('%', :pl3Code, '%')))
                    order by agent.id.positionId, agent.parentPositionId, scope.id.pl3Code, scope.id.center
                    """,
            countQuery = """
                    select count(scope)
                    from TimesheetPosition agent, TimesheetScope scope,
                         TimesheetSyncRun daily, TimesheetSyncRun monthly
                    where agent.id.syncRunId = daily.id
                      and daily.kind = 'DAILY'
                      and daily.status = 'ACTIVE'
                      and scope.id.syncRunId = monthly.id
                      and monthly.kind = 'MONTHLY'
                      and monthly.status = 'ACTIVE'
                      and agent.roleType = 'AGENT'
                      and agent.parentPositionId = scope.id.supervisorPositionId
                      and (:center = '' or scope.id.center = :center)
                      and (:agent = ''
                           or lower(agent.id.positionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = agent.id.positionId
                                  and lower(occupant.name) like lower(concat('%', :agent, '%'))))
                      and (:supervisor = ''
                           or lower(agent.parentPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = agent.parentPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(scope.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or lower(coalesce(scope.pl3Name, '')) like lower(concat('%', :pl3Code, '%')))
                    """)
    Page<DerivedAssignment> searchActiveAssignments(
            @Param("center") String center,
            @Param("agent") String agent,
            @Param("supervisor") String supervisor,
            @Param("pl3Code") String pl3Code,
            Pageable pageable);

    /**
     * Daily AGENT seat crossed with a Monthly Supervisor × PL3 scope.
     */
    interface DerivedAssignment {
        String getAgentPositionId();

        String getSupervisorPositionId();

        String getPl3Code();

        String getPl3Name();

        String getCenter();
    }

    /**
     * One AGENT seat and its walked parent positions.
     */
    interface PositionChain {
        String getAgentPositionId();

        String getSupervisorPositionId();

        String getSrManagerPositionId();

        String getCenter();
    }

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
