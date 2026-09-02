package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Monthly KPI facts.
 */
public interface TimesheetKpiRepository extends JpaRepository<TimesheetKpi, TimesheetKpi.Id> {

    /**
     * Distinct countries for a Supervisor position and PL3.
     *
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @return countries
     */
    @Query("""
            select distinct k.id.customerCountry
            from TimesheetKpi k, TimesheetSyncRun r
            where k.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and k.id.supervisorPositionId = :positionId
              and k.id.pl3Code = :pl3Code
            order by k.id.customerCountry
            """)
    List<String> findActiveCountries(
            @Param("positionId") String positionId, @Param("pl3Code") String pl3Code);

    /**
     * KPI rows for selected countries.
     *
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @param countries countries
     * @return KPI rows
     */
    @Query("""
            select k
            from TimesheetKpi k, TimesheetSyncRun r
            where k.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and k.id.supervisorPositionId = :positionId
              and k.id.pl3Code = :pl3Code
              and k.id.customerCountry in :countries
            order by k.id.customerCountry, k.id.carrier, k.id.site
            """)
    List<TimesheetKpi> findActiveKpis(
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code,
            @Param("countries") List<String> countries);

    /**
     * All ACTIVE Monthly KPI rows for a Supervisor position and PL3.
     *
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @return KPI rows
     */
    @Query("""
            select k
            from TimesheetKpi k, TimesheetSyncRun r
            where k.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and k.id.supervisorPositionId = :positionId
              and k.id.pl3Code = :pl3Code
            """)
    List<TimesheetKpi> findActiveKpis(
            @Param("positionId") String positionId, @Param("pl3Code") String pl3Code);

    /**
     * Sums Delivery HC for one KPI key.
     *
     * @param positionId supervisor position
     * @param pl3Code PL3
     * @param carrier carrier
     * @param site site
     * @param country country
     * @return hc or 0
     */
    @Query("""
            select coalesce(sum(k.hc), 0)
            from TimesheetKpi k, TimesheetSyncRun r
            where k.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
              and k.id.supervisorPositionId = :positionId
              and k.id.pl3Code = :pl3Code
              and k.id.carrier = :carrier
              and k.id.site = :site
              and k.id.customerCountry = :country
            """)
    BigDecimal sumActiveHeadcount(
            @Param("positionId") String positionId,
            @Param("pl3Code") String pl3Code,
            @Param("carrier") String carrier,
            @Param("site") String site,
            @Param("country") String country);

    /**
     * Total Delivery HC in the ACTIVE Monthly snapshot.
     *
     * @return total hc
     */
    @Query("""
            select coalesce(sum(k.hc), 0)
            from TimesheetKpi k, TimesheetSyncRun r
            where k.id.syncRunId = r.id
              and r.kind = 'MONTHLY'
              and r.status = 'ACTIVE'
            """)
    BigDecimal sumActiveHeadcount();

    /**
     * ACTIVE Monthly Delivery HC rows for the Timesheet Sync browser.
     *
     * @param center exact center; blank matches all
     * @param supervisor Supervisor position id or occupant name; blank matches all
     * @param pl3Code PL3 code or name fragment; blank matches all
     * @param pageable page
     * @return KPI rows
     */
    @Query(
            value = """
                    select k
                    from TimesheetKpi k, TimesheetSyncRun r
                    where k.id.syncRunId = r.id
                      and r.kind = 'MONTHLY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or k.id.center = :center)
                      and (:supervisor = ''
                           or lower(k.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = k.id.supervisorPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(k.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or exists (
                                select 1
                                from TimesheetScope scope
                                where scope.id.syncRunId = k.id.syncRunId
                                  and scope.id.supervisorPositionId = k.id.supervisorPositionId
                                  and scope.id.pl3Code = k.id.pl3Code
                                  and scope.id.center = k.id.center
                                  and lower(coalesce(scope.pl3Name, '')) like lower(concat('%', :pl3Code, '%'))))
                    order by k.id.center, k.id.supervisorPositionId, k.id.pl3Code, k.id.customerCountry, k.id.carrier, k.id.site
                    """,
            countQuery = """
                    select count(k)
                    from TimesheetKpi k, TimesheetSyncRun r
                    where k.id.syncRunId = r.id
                      and r.kind = 'MONTHLY'
                      and r.status = 'ACTIVE'
                      and (:center = '' or k.id.center = :center)
                      and (:supervisor = ''
                           or lower(k.id.supervisorPositionId) like lower(concat('%', :supervisor, '%'))
                           or exists (
                                select 1
                                from TimesheetPerson occupant, TimesheetSyncRun occupantRun
                                where occupant.id.syncRunId = occupantRun.id
                                  and occupantRun.kind = 'DAILY'
                                  and occupantRun.status = 'ACTIVE'
                                  and occupant.positionId = k.id.supervisorPositionId
                                  and lower(occupant.name) like lower(concat('%', :supervisor, '%'))))
                      and (:pl3Code = ''
                           or lower(k.id.pl3Code) like lower(concat('%', :pl3Code, '%'))
                           or exists (
                                select 1
                                from TimesheetScope scope
                                where scope.id.syncRunId = k.id.syncRunId
                                  and scope.id.supervisorPositionId = k.id.supervisorPositionId
                                  and scope.id.pl3Code = k.id.pl3Code
                                  and scope.id.center = k.id.center
                                  and lower(coalesce(scope.pl3Name, '')) like lower(concat('%', :pl3Code, '%'))))
                    """)
    Page<TimesheetKpi> searchActive(
            @Param("center") String center,
            @Param("supervisor") String supervisor,
            @Param("pl3Code") String pl3Code,
            Pageable pageable);

    /**
     * Drops Monthly KPI rows that are not the kept snapshot.
     *
     * @param keepRunId ACTIVE Monthly run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TimesheetKpi k where k.id.syncRunId <> :keepRunId")
    int deleteBySyncRunIdNot(@Param("keepRunId") UUID keepRunId);
}
