package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.Optional;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Resolves a person by bindable position in the ACTIVE Daily snapshot.
     *
     * @param positionId emp or occupied management position
     * @return person if present
     */
    @Query("""
            select p
            from TimesheetPerson p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.empPositionId = :positionId
            """)
    Optional<TimesheetPerson> findActiveByEmpPositionId(@Param("positionId") String positionId);

    /**
     * People who appear in this Center in the ACTIVE Daily snapshot.
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
                      and p.empPositionId is not null
                      and p.empPositionId <> ''
                      and (:name = '' or lower(p.name) like lower(concat('%', :name, '%')))
                    order by p.name, p.id.ccgid
                    """,
            countQuery = """
                    select count(p)
                    from TimesheetPerson p, TimesheetSyncRun r
                    where p.id.syncRunId = r.id
                      and r.kind = 'DAILY'
                      and r.status = 'ACTIVE'
                      and p.center = :center
                      and p.empPositionId is not null
                      and p.empPositionId <> ''
                      and (:name = '' or lower(p.name) like lower(concat('%', :name, '%')))
                    """)
    Page<TimesheetPerson> findActiveByCenter(
            @Param("center") String center, @Param("name") String name, Pageable pageable);

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
     * Whether this bindable position belongs to the Center in the ACTIVE Daily snapshot.
     *
     * @param positionId emp or occupied management position
     * @param center GBS center
     * @return true when present
     */
    @Query("""
            select count(p) > 0
            from TimesheetPerson p, TimesheetSyncRun r
            where p.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and p.empPositionId = :positionId
              and p.center = :center
            """)
    boolean existsActivePositionInCenter(
            @Param("positionId") String positionId, @Param("center") String center);
}
