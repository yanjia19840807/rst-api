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
     * ACTIVE Daily AGENT seats with the parent chain walked to Domain Head.
     *
     * @param center exact Agent-seat center; blank matches all
     * @param q position id or occupant name on any of the four levels; blank matches all
     * @param pageable page
     * @return one row per AGENT position
     */
    @Query(
            value = """
                    select agent.id.positionId as agentPositionId,
                           agent.parentPositionId as supervisorPositionId,
                           supervisor.parentPositionId as srManagerPositionId,
                           srManager.parentPositionId as domainHeadPositionId,
                           agent.center as center
                    from TimesheetPosition agent
                    join TimesheetSyncRun r on agent.id.syncRunId = r.id
                    left join TimesheetPosition supervisor
                      on supervisor.id.syncRunId = agent.id.syncRunId
                     and supervisor.id.positionId = agent.parentPositionId
                    left join TimesheetPosition srManager
                      on srManager.id.syncRunId = agent.id.syncRunId
                     and srManager.id.positionId = supervisor.parentPositionId
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.roleType = 'AGENT'
                      and (:center = '' or agent.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(agent.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisor.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(srManager.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId in (
                                        agent.id.positionId,
                                        agent.parentPositionId,
                                        supervisor.parentPositionId,
                                        srManager.parentPositionId)
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
                    left join TimesheetPosition srManager
                      on srManager.id.syncRunId = agent.id.syncRunId
                     and srManager.id.positionId = supervisor.parentPositionId
                    where r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and agent.roleType = 'AGENT'
                      and (:center = '' or agent.center = :center)
                      and (:q = ''
                           or lower(agent.id.positionId) like lower(concat('%', :q, '%'))
                           or lower(coalesce(agent.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(supervisor.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(srManager.parentPositionId, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId in (
                                        agent.id.positionId,
                                        agent.parentPositionId,
                                        supervisor.parentPositionId,
                                        srManager.parentPositionId)
                                  and lower(occupant.name) like lower(concat('%', :q, '%'))))
                    """)
    Page<PositionChain> searchActiveChains(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * One AGENT seat and its walked parent positions.
     */
    interface PositionChain {
        String getAgentPositionId();

        String getSupervisorPositionId();

        String getSrManagerPositionId();

        String getDomainHeadPositionId();

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
