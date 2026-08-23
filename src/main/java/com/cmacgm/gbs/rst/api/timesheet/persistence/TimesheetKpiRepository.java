package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.math.BigDecimal;
import java.util.List;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
