package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;
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
    List<TimesheetPosition> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * Finds an ACTIVE Daily position node.
     *
     * @param positionId position
     * @param roleType AGENT / SUPERVISOR / SR_MANAGER
     * @return position when present
     */
    @Query("""
            select p
            from TimesheetPosition p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.id.positionId = :positionId
              and p.id.roleType = :roleType
            """)
    Optional<TimesheetPosition> findActiveByPositionIdAndRole(
            @Param("positionId") String positionId, @Param("roleType") String roleType);

    /**
     * ACTIVE Daily position nodes.
     *
     * @param center exact run center; blank matches all
     * @param q position, parent, role or occupant name; blank matches all
     * @param pageable page
     * @return one row per (position_id, role_type)
     */
    @Query(
            value = """
                    select p.id.positionId as positionId,
                           p.id.roleType as roleType,
                           p.parentPositionId as parentPositionId,
                           p.parentRoleType as parentRoleType,
                           r.center as center
                    from TimesheetPosition p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(p.id.roleType) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat
                                where occupant.id.syncRunId = r.id
                                  and seat.id.syncRunId = r.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and seat.id.positionId = p.id.positionId
                                  and seat.id.roleType = p.id.roleType
                                  and lower(occupant.name) like lower(concat('%', :q, '%'))))
                    order by r.center, p.id.positionId,
                             case p.id.roleType
                                 when 'AGENT' then 0
                                 when 'SUPERVISOR' then 1
                                 else 2
                             end
                    """,
            countQuery = """
                    select count(p)
                    from TimesheetPosition p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(p.id.roleType) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat
                                where occupant.id.syncRunId = r.id
                                  and seat.id.syncRunId = r.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and seat.id.positionId = p.id.positionId
                                  and seat.id.roleType = p.id.roleType
                                  and lower(occupant.name) like lower(concat('%', :q, '%'))))
                    """)
    Page<PositionNode> searchActiveNodes(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

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
                           r.center as center
                    from TimesheetPosition agent
                    join TimesheetSyncRun r on agent.id.syncRunId = r.id
                    left join TimesheetPosition supervisor
                      on supervisor.id.syncRunId = agent.id.syncRunId
                     and supervisor.id.positionId = agent.parentPositionId
                     and supervisor.id.roleType = coalesce(agent.parentRoleType, 'SUPERVISOR')
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.id.roleType = 'AGENT'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(agent.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisor.parentPositionId, '')) like lower(concat('%', :q, '%'))
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
                                  and occupant.center = r.center
                                  and seat.id.positionId in (
                                        agent.id.positionId,
                                        agent.parentPositionId,
                                        supervisor.parentPositionId)
                                  and lower(occupant.name) like lower(concat('%', :q, '%'))))
                    order by r.center, agent.id.positionId
                    """,
            countQuery = """
                    select count(agent)
                    from TimesheetPosition agent
                    join TimesheetSyncRun r on agent.id.syncRunId = r.id
                    left join TimesheetPosition supervisor
                      on supervisor.id.syncRunId = agent.id.syncRunId
                     and supervisor.id.positionId = agent.parentPositionId
                     and supervisor.id.roleType = coalesce(agent.parentRoleType, 'SUPERVISOR')
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.id.roleType = 'AGENT'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(agent.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisor.parentPositionId, '')) like lower(concat('%', :q, '%'))
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
                                  and occupant.center = r.center
                                  and seat.id.positionId in (
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
                      and scope.id.center = monthly.center
                      and daily.center = monthly.center
                      and daily.center = scope.id.center
                      and agent.id.roleType = 'AGENT'
                      and agent.parentPositionId = scope.id.supervisorPositionId
                      and (:center = '' or scope.id.center = :center)
                      and (:agent = ''
                           or lower(agent.id.positionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat,
                                     TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = daily.center
                                  and occupant.center = daily.center
                                  and seat.id.positionId = agent.id.positionId
                                  and lower(occupant.name) like lower(concat('%', :agent, '%'))))
                      and (:supervisor = ''
                           or lower(agent.parentPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat,
                                     TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = daily.center
                                  and occupant.center = daily.center
                                  and seat.id.positionId = agent.parentPositionId
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
                      and scope.id.center = monthly.center
                      and daily.center = monthly.center
                      and daily.center = scope.id.center
                      and agent.id.roleType = 'AGENT'
                      and agent.parentPositionId = scope.id.supervisorPositionId
                      and (:center = '' or scope.id.center = :center)
                      and (:agent = ''
                           or lower(agent.id.positionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat,
                                     TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = daily.center
                                  and occupant.center = daily.center
                                  and seat.id.positionId = agent.id.positionId
                                  and lower(occupant.name) like lower(concat('%', :agent, '%'))))
                      and (:supervisor = ''
                           or lower(agent.parentPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetPersonPositionRole seat,
                                     TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = daily.center
                                  and occupant.center = daily.center
                                  and seat.id.positionId = agent.parentPositionId
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
     * One Daily position node.
     */
    interface PositionNode {
        String getPositionId();

        String getRoleType();

        String getParentPositionId();

        String getParentRoleType();

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
     * Drops Daily position rows for other runs of this kind and Center.
     *
     * @param kind DAILY
     * @param center GBS center
     * @param keepRunId ACTIVE Daily run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from TimesheetPosition p
            where p.id.syncRunId in (
                select r.id from TimesheetSyncRun r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
