package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Daily position tree.
 */
public interface OrgPositionRepository
        extends JpaRepository<OrgPosition, OrgPosition.Id> {

    /**
     * Finds an ACTIVE Daily position.
     *
     * @param positionId position
     * @return position when present
     */
    @Query("""
            select p
            from OrgPosition p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.id.positionId = :positionId
            """)
    List<OrgPosition> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * Finds an ACTIVE Daily position node.
     *
     * @param positionId position
     * @param roleType AGENT / SUPERVISOR / SR_MANAGER
     * @return position when present
     */
    @Query("""
            select p
            from OrgPosition p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.id.positionId = :positionId
              and p.id.roleType = :roleType
            """)
    Optional<OrgPosition> findActiveByPositionIdAndRole(
            @Param("positionId") String positionId, @Param("roleType") String roleType);

    /**
     * ACTIVE Daily positions in a Center that do not carry the given role.
     * A position with that role on any row is omitted entirely.
     *
     * @param center GBS center
     * @param roleType role to exclude, such as AGENT
     * @return position ids
     */
    @Query("""
            select distinct p.id.positionId
            from OrgPosition p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and r.center = :center
              and not exists (
                  select 1
                  from OrgPosition excluded
                  where excluded.id.syncRunId = r.id
                    and excluded.id.positionId = p.id.positionId
                    and excluded.id.roleType = :roleType
              )
            order by p.id.positionId
            """)
    List<String> findActivePositionIdsInCenterWithoutRole(
            @Param("center") String center, @Param("roleType") String roleType);

    /**
     * Roles stored for one position on the ACTIVE MyHR snapshot, including a
     * vacant Supervisor or Senior Manager position.
     *
     * @param positionId position
     * @return position nodes
     */
    @Query("""
            select p
            from OrgPosition p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and p.id.positionId = :positionId
            """)
    List<OrgPosition> findActiveMyHrByPositionId(@Param("positionId") String positionId);

    /**
     * MyHR positions in a Center that do not carry the given role.
     * A vacant parent is included when a person in the Center sits one or two
     * levels below it.
     *
     * @param center GBS center
     * @param roleType role to exclude
     * @return position ids
     */
    @Query("""
            select distinct p.id.positionId
            from OrgPosition p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and p.id.roleType <> :roleType
              and not exists (
                  select 1
                  from OrgPosition excluded
                  where excluded.id.syncRunId = r.id
                    and excluded.id.positionId = p.id.positionId
                    and excluded.id.roleType = :roleType
              )
              and (
                  exists (
                      select 1
                      from OrgSeat seat, OrgPerson person
                      where seat.id.syncRunId = r.id
                        and person.id.syncRunId = r.id
                        and person.id.ccgid = seat.id.ccgid
                        and seat.id.positionId = p.id.positionId
                        and person.center = :center
                  )
                  or exists (
                      select 1
                      from OrgPositionParent edge, OrgSeat child, OrgPerson person
                      where edge.id.syncRunId = r.id
                        and child.id.syncRunId = r.id
                        and person.id.syncRunId = r.id
                        and person.id.ccgid = child.id.ccgid
                        and edge.id.parentPositionId = p.id.positionId
                        and edge.id.positionId = child.id.positionId
                        and person.center = :center
                  )
                  or exists (
                      select 1
                      from OrgPositionParent edge, OrgPositionParent mid, OrgSeat child, OrgPerson person
                      where edge.id.syncRunId = r.id
                        and mid.id.syncRunId = r.id
                        and child.id.syncRunId = r.id
                        and person.id.syncRunId = r.id
                        and person.id.ccgid = child.id.ccgid
                        and edge.id.parentPositionId = p.id.positionId
                        and edge.id.positionId = mid.id.parentPositionId
                        and mid.id.positionId = child.id.positionId
                        and person.center = :center
                  )
              )
            order by p.id.positionId
            """)
    List<String> findActiveMyHrPositionIdsInCenterWithoutRole(
            @Param("center") String center, @Param("roleType") String roleType);

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
                           r.center as center
                    from OrgPosition p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(p.id.roleType) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from OrgPositionParent edge
                                where edge.id.syncRunId = r.id
                                  and edge.id.positionId = p.id.positionId
                                  and edge.id.roleType = p.id.roleType
                                  and lower(edge.id.parentPositionId) like lower(concat('%', :q, '%')))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat
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
                                 when 'SR_MANAGER' then 2
                                 else 3
                             end
                    """,
            countQuery = """
                    select count(p)
                    from OrgPosition p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(p.id.roleType) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from OrgPositionParent edge
                                where edge.id.syncRunId = r.id
                                  and edge.id.positionId = p.id.positionId
                                  and edge.id.roleType = p.id.roleType
                                  and lower(edge.id.parentPositionId) like lower(concat('%', :q, '%')))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat
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
                           supervisorEdge.id.parentPositionId as supervisorPositionId,
                           managerEdge.id.parentPositionId as srManagerPositionId,
                           r.center as center
                    from OrgPosition agent
                    join SourceSnapshot r on agent.id.syncRunId = r.id
                    left join OrgPositionParent supervisorEdge
                      on supervisorEdge.id.syncRunId = agent.id.syncRunId
                     and supervisorEdge.id.positionId = agent.id.positionId
                     and supervisorEdge.id.roleType = agent.id.roleType
                    left join OrgPositionParent managerEdge
                      on managerEdge.id.syncRunId = agent.id.syncRunId
                     and managerEdge.id.positionId = supervisorEdge.id.parentPositionId
                     and managerEdge.id.roleType = supervisorEdge.id.parentRoleType
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.id.roleType = 'AGENT'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisorEdge.id.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(managerEdge.id.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat,
                                     SourceSnapshot occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = r.center
                                  and occupant.center = r.center
                                  and seat.id.positionId in (
                                        agent.id.positionId,
                                        supervisorEdge.id.parentPositionId,
                                        managerEdge.id.parentPositionId)
                                  and lower(occupant.name) like lower(concat('%', :q, '%'))))
                    order by r.center, agent.id.positionId
                    """,
            countQuery = """
                    select count(agent)
                    from OrgPosition agent
                    join SourceSnapshot r on agent.id.syncRunId = r.id
                    left join OrgPositionParent supervisorEdge
                      on supervisorEdge.id.syncRunId = agent.id.syncRunId
                     and supervisorEdge.id.positionId = agent.id.positionId
                     and supervisorEdge.id.roleType = agent.id.roleType
                    left join OrgPositionParent managerEdge
                      on managerEdge.id.syncRunId = agent.id.syncRunId
                     and managerEdge.id.positionId = supervisorEdge.id.parentPositionId
                     and managerEdge.id.roleType = supervisorEdge.id.parentRoleType
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.id.roleType = 'AGENT'
                      and (:center = '' or r.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisorEdge.id.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(managerEdge.id.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat,
                                     SourceSnapshot occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = r.center
                                  and occupant.center = r.center
                                  and seat.id.positionId in (
                                        agent.id.positionId,
                                        supervisorEdge.id.parentPositionId,
                                        managerEdge.id.parentPositionId)
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
                           edge.id.parentPositionId as supervisorPositionId,
                           scope.id.pl3Code as pl3Code,
                           scope.pl3Name as pl3Name,
                           scope.id.center as center
                    from OrgPosition agent, OrgPositionParent edge, MonthlyScope scope,
                         SourceSnapshot daily, SourceSnapshot monthly
                    where agent.id.syncRunId = daily.id
                      and edge.id.syncRunId = daily.id
                      and edge.id.positionId = agent.id.positionId
                      and edge.id.roleType = agent.id.roleType
                      and daily.kind = 'DAILY'
                      and daily.status = 'ACTIVE'
                      and scope.id.syncRunId = monthly.id
                      and monthly.kind = 'MONTHLY'
                      and monthly.status = 'ACTIVE'
                      and scope.id.center = monthly.center
                      and daily.center = monthly.center
                      and daily.center = scope.id.center
                      and agent.id.roleType = 'AGENT'
                      and edge.id.parentPositionId = scope.id.supervisorPositionId
                      and (:center = '' or scope.id.center = :center)
                      and (:agent = ''
                           or lower(agent.id.positionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat,
                                     SourceSnapshot occupantRun
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
                           or lower(edge.id.parentPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat,
                                     SourceSnapshot occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = daily.center
                                  and occupant.center = daily.center
                                  and seat.id.positionId = edge.id.parentPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(scope.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or lower(coalesce(scope.pl3Name, '')) like lower(concat('%', :pl3Code, '%')))
                    order by agent.id.positionId, edge.id.parentPositionId, scope.id.pl3Code, scope.id.center
                    """,
            countQuery = """
                    select count(scope)
                    from OrgPosition agent, OrgPositionParent edge, MonthlyScope scope,
                         SourceSnapshot daily, SourceSnapshot monthly
                    where agent.id.syncRunId = daily.id
                      and edge.id.syncRunId = daily.id
                      and edge.id.positionId = agent.id.positionId
                      and edge.id.roleType = agent.id.roleType
                      and daily.kind = 'DAILY'
                      and daily.status = 'ACTIVE'
                      and scope.id.syncRunId = monthly.id
                      and monthly.kind = 'MONTHLY'
                      and monthly.status = 'ACTIVE'
                      and scope.id.center = monthly.center
                      and daily.center = monthly.center
                      and daily.center = scope.id.center
                      and agent.id.roleType = 'AGENT'
                      and edge.id.parentPositionId = scope.id.supervisorPositionId
                      and (:center = '' or scope.id.center = :center)
                      and (:agent = ''
                           or lower(agent.id.positionId) like lower(concat('%', :agent, '%'))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat,
                                     SourceSnapshot occupantRun
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
                           or lower(edge.id.parentPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from OrgPerson occupant, OrgSeat seat,
                                     SourceSnapshot occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and seat.id.syncRunId = occupantRun.id
                                  and seat.id.ccgid = occupant.id.ccgid
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupantRun.center = daily.center
                                  and occupant.center = daily.center
                                  and seat.id.positionId = edge.id.parentPositionId
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
     * Positions occupied by people of one center in the ACTIVE MyHR snapshot.
     *
     * Includes a vacant Supervisor or Senior Manager when someone in the Center
     * sits one or two levels below it.
     *
     * @param center GBS center
     * @param q position, role, occupant name, or CCGID
     * @param pageable page
     * @return position nodes
     */
    @Query(
            value = """
                    select p
                    from OrgPosition p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'MYHR'
                      and r.status = 'ACTIVE'
                      and (
                          exists (
                              select 1
                              from OrgSeat s, OrgPerson person
                              where s.id.syncRunId = r.id
                                and person.id.syncRunId = r.id
                                and person.id.ccgid = s.id.ccgid
                                and s.id.positionId = p.id.positionId
                                and s.id.roleType = p.id.roleType
                                and person.center = :center
                                and (:q = ''
                                     or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                                     or lower(p.id.roleType) like lower(concat('%', :q, '%'))
                                     or lower(person.name) like lower(concat('%', :q, '%'))
                                     or lower(s.id.ccgid) like lower(concat('%', :q, '%'))))
                          or exists (
                              select 1
                              from OrgPositionParent edge, OrgSeat child, OrgPerson person
                              where edge.id.syncRunId = r.id
                                and child.id.syncRunId = r.id
                                and person.id.syncRunId = r.id
                                and person.id.ccgid = child.id.ccgid
                                and edge.id.parentPositionId = p.id.positionId
                                and edge.id.positionId = child.id.positionId
                                and person.center = :center
                                and not exists (
                                    select 1
                                    from OrgSeat own
                                    where own.id.syncRunId = r.id
                                      and own.id.positionId = p.id.positionId
                                      and own.id.roleType = p.id.roleType)
                                and (:q = ''
                                     or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                                     or lower(p.id.roleType) like lower(concat('%', :q, '%'))))
                          or exists (
                              select 1
                              from OrgPositionParent edge, OrgPositionParent mid,
                                   OrgSeat child, OrgPerson person
                              where edge.id.syncRunId = r.id
                                and mid.id.syncRunId = r.id
                                and child.id.syncRunId = r.id
                                and person.id.syncRunId = r.id
                                and person.id.ccgid = child.id.ccgid
                                and edge.id.parentPositionId = p.id.positionId
                                and edge.id.positionId = mid.id.parentPositionId
                                and mid.id.positionId = child.id.positionId
                                and person.center = :center
                                and not exists (
                                    select 1
                                    from OrgSeat own
                                    where own.id.syncRunId = r.id
                                      and own.id.positionId = p.id.positionId
                                      and own.id.roleType = p.id.roleType)
                                and (:q = ''
                                     or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                                     or lower(p.id.roleType) like lower(concat('%', :q, '%')))))
                    order by p.id.positionId, p.id.roleType
                    """,
            countQuery = """
                    select count(p)
                    from OrgPosition p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'MYHR'
                      and r.status = 'ACTIVE'
                      and (
                          exists (
                              select 1
                              from OrgSeat s, OrgPerson person
                              where s.id.syncRunId = r.id
                                and person.id.syncRunId = r.id
                                and person.id.ccgid = s.id.ccgid
                                and s.id.positionId = p.id.positionId
                                and s.id.roleType = p.id.roleType
                                and person.center = :center
                                and (:q = ''
                                     or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                                     or lower(p.id.roleType) like lower(concat('%', :q, '%'))
                                     or lower(person.name) like lower(concat('%', :q, '%'))
                                     or lower(s.id.ccgid) like lower(concat('%', :q, '%'))))
                          or exists (
                              select 1
                              from OrgPositionParent edge, OrgSeat child, OrgPerson person
                              where edge.id.syncRunId = r.id
                                and child.id.syncRunId = r.id
                                and person.id.syncRunId = r.id
                                and person.id.ccgid = child.id.ccgid
                                and edge.id.parentPositionId = p.id.positionId
                                and edge.id.positionId = child.id.positionId
                                and person.center = :center
                                and not exists (
                                    select 1
                                    from OrgSeat own
                                    where own.id.syncRunId = r.id
                                      and own.id.positionId = p.id.positionId
                                      and own.id.roleType = p.id.roleType)
                                and (:q = ''
                                     or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                                     or lower(p.id.roleType) like lower(concat('%', :q, '%'))))
                          or exists (
                              select 1
                              from OrgPositionParent edge, OrgPositionParent mid,
                                   OrgSeat child, OrgPerson person
                              where edge.id.syncRunId = r.id
                                and mid.id.syncRunId = r.id
                                and child.id.syncRunId = r.id
                                and person.id.syncRunId = r.id
                                and person.id.ccgid = child.id.ccgid
                                and edge.id.parentPositionId = p.id.positionId
                                and edge.id.positionId = mid.id.parentPositionId
                                and mid.id.positionId = child.id.positionId
                                and person.center = :center
                                and not exists (
                                    select 1
                                    from OrgSeat own
                                    where own.id.syncRunId = r.id
                                      and own.id.positionId = p.id.positionId
                                      and own.id.roleType = p.id.roleType)
                                and (:q = ''
                                     or lower(p.id.positionId) like lower(concat('%', :q, '%'))
                                     or lower(p.id.roleType) like lower(concat('%', :q, '%')))))
                    """)
    Page<OrgPosition> searchActiveMyHr(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

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
            delete from OrgPosition p
            where p.id.syncRunId in (
                select r.id from SourceSnapshot r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
