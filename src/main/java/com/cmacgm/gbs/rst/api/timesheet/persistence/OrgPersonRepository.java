package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Daily person identities.
 */
public interface OrgPersonRepository extends JpaRepository<OrgPerson, OrgPerson.Id> {

    /**
     * Resolves a display name from the ACTIVE Daily snapshot.
     *
     * @param ccgid identity
     * @return name if present
     */
    @Query("""
            select p.name
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
            """)
    Optional<String> findActiveNameByCcgid(@Param("ccgid") String ccgid);

    /**
     * Resolves a person from the ACTIVE Daily snapshot.
     *
     * @param ccgid identity
     * @return person if present
     */
    @Query("""
            select p
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
            """)
    Optional<OrgPerson> findActiveByCcgid(@Param("ccgid") String ccgid);

    /**
     * ACTIVE Daily people for the given CCGIDs.
     *
     * @param ccgids identities
     * @return people
     */
    @Query("""
            select p
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) in :ccgids
            """)
    List<OrgPerson> findActiveByCcgidIn(@Param("ccgids") Collection<String> ccgids);

    /**
     * Occupants of a bindable position in the ACTIVE Daily snapshot.
     *
     * @param positionId occupied position
     * @return people
     */
    @Query("""
            select distinct p
            from OrgPerson p, OrgSeat o, SourceSnapshot r
            where p.id.syncRunId = r.id
              and o.id.syncRunId = r.id
              and o.id.ccgid = p.id.ccgid
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and o.id.positionId = :positionId
            order by p.id.ccgid
            """)
    List<OrgPerson> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * Daily Agents whose seat reports to this Supervisor occupant.
     *
     * @param supervisorCcgid supervisor
     * @return agents
     */
    @Query("""
            select agent
            from OrgPerson supervisor, OrgSeat supervisorSeat,
                 OrgPosition child, OrgPositionParent edge,
                 OrgPerson agent, OrgSeat agentSeat,
                 SourceSnapshot daily
            where supervisor.id.syncRunId = daily.id
              and supervisorSeat.id.syncRunId = daily.id
              and child.id.syncRunId = daily.id
              and edge.id.syncRunId = daily.id
              and agent.id.syncRunId = daily.id
              and agentSeat.id.syncRunId = daily.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and supervisor.center = daily.center
              and agent.center = daily.center
              and upper(supervisor.id.ccgid) = upper(:supervisorCcgid)
              and supervisorSeat.id.ccgid = supervisor.id.ccgid
              and supervisorSeat.id.roleType = 'SUPERVISOR'
              and edge.id.positionId = child.id.positionId
              and edge.id.roleType = child.id.roleType
              and edge.id.parentPositionId = supervisorSeat.id.positionId
              and edge.id.parentRoleType = 'SUPERVISOR'
              and child.id.roleType = 'AGENT'
              and agentSeat.id.ccgid = agent.id.ccgid
              and agentSeat.id.positionId = child.id.positionId
              and agentSeat.id.roleType = 'AGENT'
            order by agent.name, agent.id.ccgid
            """)
    List<OrgPerson> findActiveReportsBySupervisorCcgid(
            @Param("supervisorCcgid") String supervisorCcgid);

    /**
     * Occupants of bindable positions in the ACTIVE Daily snapshot.
     *
     * @param positionIds occupied positions
     * @return people for those seats
     */
    @Query("""
            select distinct p
            from OrgPerson p, OrgSeat o, SourceSnapshot r
            where p.id.syncRunId = r.id
              and o.id.syncRunId = r.id
              and o.id.ccgid = p.id.ccgid
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and o.id.positionId in :positionIds
            order by p.id.ccgid
            """)
    List<OrgPerson> findActiveByPositionIdIn(@Param("positionIds") Collection<String> positionIds);

    /**
     * People in this Center in the ACTIVE Daily snapshot.
     *
     * @param center GBS center
     * @param name optional name / email / CCGID fragment; blank matches all
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select p
                    from OrgPerson p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and r.center = :center
                      and p.center = :center
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :name, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(p)
                    from OrgPerson p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and r.center = :center
                      and p.center = :center
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :name, '%')))
                    """)
    Page<OrgPerson> findActiveByCenter(
            @Param("center") String center, @Param("name") String name, Pageable pageable);

    /**
     * People in this Center who occupy one of the given positions.
     *
     * @param center GBS center
     * @param positionIds positions whose occupants are eligible
     * @param name optional name / email / CCGID fragment; blank matches all
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select distinct p
                    from OrgPerson p, OrgSeat o, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and o.id.syncRunId = r.id
                      and o.id.ccgid = p.id.ccgid
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and r.center = :center
                      and p.center = :center
                      and o.id.positionId in :positionIds
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :name, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(distinct p)
                    from OrgPerson p, OrgSeat o, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and o.id.syncRunId = r.id
                      and o.id.ccgid = p.id.ccgid
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and r.center = :center
                      and p.center = :center
                      and o.id.positionId in :positionIds
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :name, '%')))
                    """)
    Page<OrgPerson> findActiveOccupantsInCenter(
            @Param("center") String center,
            @Param("positionIds") Collection<String> positionIds,
            @Param("name") String name,
            Pageable pageable);

    /**
     * Active people whose name, email or CCGID contains the query.
     *
     * @param query name / email / CCGID fragment; blank matches all
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select distinct p
                    from OrgPerson p, OrgSeat o, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and o.id.syncRunId = r.id
                      and o.id.ccgid = p.id.ccgid
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.center = r.center
                      and (:query = ''
                           or lower(p.name) like lower(concat('%', :query, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :query, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :query, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(distinct p)
                    from OrgPerson p, OrgSeat o, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and o.id.syncRunId = r.id
                      and o.id.ccgid = p.id.ccgid
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.center = r.center
                      and (:query = ''
                           or lower(p.name) like lower(concat('%', :query, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :query, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :query, '%')))
                    """)
    Page<OrgPerson> findActiveByNameOrCcgid(@Param("query") String query, Pageable pageable);

    /**
     * Whether this person belongs to the Center in the ACTIVE Daily snapshot.
     *
     * @param ccgid identity
     * @param center GBS center
     * @return true when present
     */
    @Query("""
            select count(p) > 0
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and r.center = :center
              and upper(p.id.ccgid) = upper(:ccgid)
              and p.center = :center
            """)
    boolean existsActiveInCenter(@Param("ccgid") String ccgid, @Param("center") String center);

    /**
     * ACTIVE Daily people for the Timesheet Sync browser.
     *
     * @param center exact center; blank matches all
     * @param q name / CCGID / emp id / email / job role / position fragment; blank matches all
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select p
                    from OrgPerson p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.center = r.center
                      and (:center = '' or p.center = :center)
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.empId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.jobRole, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from OrgSeat o
                                where o.id.syncRunId = r.id
                                  and o.id.ccgid = p.id.ccgid
                                  and lower(o.id.positionId) like lower(concat('%', :q, '%'))))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(p)
                    from OrgPerson p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.center = r.center
                      and (:center = '' or p.center = :center)
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.empId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.jobRole, '')) like lower(concat('%', :q, '%'))
                           or exists (
                                select 1
                                from OrgSeat o
                                where o.id.syncRunId = r.id
                                  and o.id.ccgid = p.id.ccgid
                                  and lower(o.id.positionId) like lower(concat('%', :q, '%'))))
                    """)
    Page<OrgPerson> searchActive(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * People in the ACTIVE MyHR snapshot for one center.
     *
     * @param center GBS center
     * @param q name, CCGID, emp id, email, or job role
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select p
                    from OrgPerson p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'MYHR'
                      and r.status = 'ACTIVE'
                      and p.center = :center
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.empId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.jobRole, '')) like lower(concat('%', :q, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(p)
                    from OrgPerson p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'MYHR'
                      and r.status = 'ACTIVE'
                      and p.center = :center
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.empId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.jobRole, '')) like lower(concat('%', :q, '%')))
                    """)
    Page<OrgPerson> searchActiveMyHr(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * Whether this person belongs to the Center in the ACTIVE MyHR snapshot.
     *
     * @param ccgid identity
     * @param center GBS center
     * @return true when present
     */
    @Query("""
            select count(p) > 0
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and p.center = :center
              and upper(p.id.ccgid) = upper(:ccgid)
            """)
    boolean existsActiveMyHrInCenter(@Param("ccgid") String ccgid, @Param("center") String center);

    /**
     * @param center GBS center
     * @return people in the ACTIVE MyHR snapshot
     */
    @Query("""
            select count(p)
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and p.center = :center
            """)
    long countActiveMyHrInCenter(@Param("center") String center);

    /**
     * Occupants of a position in the ACTIVE MyHR snapshot.
     *
     * @param positionId MyHR position code
     * @return people
     */
    @Query("""
            select distinct p
            from OrgPerson p, OrgSeat o, SourceSnapshot r
            where p.id.syncRunId = r.id
              and o.id.syncRunId = r.id
              and o.id.ccgid = p.id.ccgid
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and o.id.positionId = :positionId
            order by p.id.ccgid
            """)
    List<OrgPerson> findActiveMyHrByPositionId(@Param("positionId") String positionId);

    /**
     * MyHR people for the given identities.
     *
     * @param ccgids identities
     * @return people
     */
    @Query("""
            select p
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) in :ccgids
            """)
    List<OrgPerson> findMyHrByCcgidIn(@Param("ccgids") Collection<String> ccgids);

    /**
     * One person in the ACTIVE MyHR snapshot.
     *
     * @param ccgid identity
     * @return person when present
     */
    @Query("""
            select p
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
            """)
    Optional<OrgPerson> findActiveMyHrByCcgid(@Param("ccgid") String ccgid);

    /**
     * People in a Center who occupy one of the positions in the ACTIVE MyHR snapshot.
     *
     * @param center GBS center
     * @param positionIds positions
     * @param name name, email, or CCGID fragment
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select distinct p
                    from OrgPerson p, OrgSeat o, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and o.id.syncRunId = r.id
                      and o.id.ccgid = p.id.ccgid
                      and r.kind = 'MYHR'
                      and r.status = 'ACTIVE'
                      and p.center = :center
                      and o.id.positionId in :positionIds
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :name, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(distinct p)
                    from OrgPerson p, OrgSeat o, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and o.id.syncRunId = r.id
                      and o.id.ccgid = p.id.ccgid
                      and r.kind = 'MYHR'
                      and r.status = 'ACTIVE'
                      and p.center = :center
                      and o.id.positionId in :positionIds
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :name, '%')))
                    """)
    Page<OrgPerson> findActiveMyHrOccupantsInCenter(
            @Param("center") String center,
            @Param("positionIds") Collection<String> positionIds,
            @Param("name") String name,
            Pageable pageable);

    /**
     * Distinct centers in the ACTIVE Daily people snapshot.
     *
     * @return centers
     */
    @Query("""
            select distinct p.center
            from OrgPerson p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.center = r.center
              and p.center is not null
              and p.center <> ''
            order by p.center
            """)
    List<String> findActiveCenters();

    /**
     * Drops Daily person rows for other runs of this kind and Center.
     *
     * @param kind DAILY
     * @param center GBS center
     * @param keepRunId ACTIVE Daily run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from OrgPerson p
            where p.id.syncRunId in (
                select r.id from SourceSnapshot r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
