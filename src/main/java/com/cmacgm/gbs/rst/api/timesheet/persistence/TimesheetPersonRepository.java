package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Daily person identities.
 */
public interface TimesheetPersonRepository extends JpaRepository<TimesheetPerson, TimesheetPerson.Id> {

    /**
     * Resolves a display name from the ACTIVE Daily snapshot.
     *
     * @param ccgid identity
     * @return name if present
     */
    @Query("""
            select p.name
            from TimesheetPerson p, TimesheetSyncRun r
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
            from TimesheetPerson p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
            """)
    Optional<TimesheetPerson> findActiveByCcgid(@Param("ccgid") String ccgid);

    /**
     * Occupant of a bindable position in the ACTIVE Daily snapshot.
     *
     * @param positionId occupied position
     * @return person if present
     */
    @Query("""
            select p
            from TimesheetPerson p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.positionId = :positionId
            """)
    Optional<TimesheetPerson> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * Position occupied by this person when it has the given role.
     *
     * @param ccgid occupant
     * @param roleType SUPERVISOR / SR_MANAGER / DOMAIN_HEAD
     * @return position id when present
     */
    @Query("""
            select p.positionId
            from TimesheetPerson p, TimesheetPosition pos, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and pos.id.syncRunId = r.id
              and pos.id.positionId = p.positionId
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
              and pos.roleType = :roleType
            """)
    Optional<String> findActivePositionIdByCcgidAndRole(
            @Param("ccgid") String ccgid, @Param("roleType") String roleType);

    /**
     * Whether this bindable position belongs to the Center.
     *
     * @param positionId occupied position
     * @param center GBS center
     * @return true when present
     */
    @Query("""
            select count(p) > 0
            from TimesheetPerson p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.positionId = :positionId
              and p.center = :center
            """)
    boolean existsActivePositionInCenter(
            @Param("positionId") String positionId, @Param("center") String center);

    /**
     * People in this Center who occupy a bindable position.
     *
     * @param center GBS center
     * @param name optional name fragment; blank matches all
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select p
                    from TimesheetPerson p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.center = :center
                      and p.positionId is not null
                      and p.positionId <> ''
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(p)
                    from TimesheetPerson p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.center = :center
                      and p.positionId is not null
                      and p.positionId <> ''
                      and (:name = ''
                           or lower(p.name) like lower(concat('%', :name, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :name, '%')))
                    """)
    Page<TimesheetPerson> findActiveByCenter(
            @Param("center") String center, @Param("name") String name, Pageable pageable);

    /**
     * Active people whose name or CCGID contains the query.
     *
     * @param query name or CCGID fragment; blank matches all
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select p
                    from TimesheetPerson p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.positionId is not null
                      and p.positionId <> ''
                      and (:query = ''
                           or lower(p.name) like lower(concat('%', :query, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :query, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(p)
                    from TimesheetPerson p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.positionId is not null
                      and p.positionId <> ''
                      and (:query = ''
                           or lower(p.name) like lower(concat('%', :query, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :query, '%')))
                    """)
    Page<TimesheetPerson> findActiveByNameOrCcgid(@Param("query") String query, Pageable pageable);

    /**
     * Whether this person belongs to the Center in the ACTIVE Daily snapshot.
     *
     * @param ccgid identity
     * @param center GBS center
     * @return true when present
     */
    @Query("""
            select count(p) > 0
            from TimesheetPerson p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and upper(p.id.ccgid) = upper(:ccgid)
              and p.center = :center
            """)
    boolean existsActiveInCenter(@Param("ccgid") String ccgid, @Param("center") String center);

    /**
     * ACTIVE Daily people for the Timesheet Sync browser.
     *
     * @param center exact center; blank matches all
     * @param q name / CCGID / emp id / email fragment; blank matches all
     * @param pageable page
     * @return people
     */
    @Query(
            value = """
                    select p
                    from TimesheetPerson p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or p.center = :center)
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.empId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(p)
                    from TimesheetPerson p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or p.center = :center)
                      and (:q = ''
                           or lower(p.name) like lower(concat('%', :q, '%'))
                           or lower(p.id.ccgid) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.empId, '')) like lower(concat('%', :q, '%'))
                           or lower(coalesce(p.email, '')) like lower(concat('%', :q, '%')))
                    """)
    Page<TimesheetPerson> searchActive(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * Distinct centers in the ACTIVE Daily people snapshot.
     *
     * @return centers
     */
    @Query("""
            select distinct p.center
            from TimesheetPerson p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.center is not null
              and p.center <> ''
            order by p.center
            """)
    List<String> findActiveCenters();

    /**
     * Drops Daily person rows that are not the kept snapshot.
     *
     * @param keepRunId ACTIVE Daily run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TimesheetPerson p where p.id.syncRunId <> :keepRunId")
    int deleteBySyncRunIdNot(@Param("keepRunId") UUID keepRunId);
}
