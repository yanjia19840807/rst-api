package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSnapshotRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimesheetSnapshotRowRepository extends JpaRepository<TimesheetSnapshotRow, UUID> {

    /**
     * Resolves a display name for a supervisor CCGID from the ACTIVE snapshot.
     *
     * @param ccgid supervisor CCGID
     * @return distinct supervisor names
     */
    @Query("""
            select distinct r.supervisorName
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.supervisorCcgid) = upper(:ccgid)
              and r.supervisorName is not null
            """)
    List<String> findSupervisorNamesByCcgid(@Param("ccgid") String ccgid);

    /**
     * Resolves a display name for an employee CCGID from the ACTIVE snapshot.
     *
     * @param ccgid employee CCGID
     * @return distinct employee names
     */
    @Query("""
            select distinct r.empName
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.empCcgid) = upper(:ccgid)
              and r.empName is not null
            """)
    List<String> findEmployeeNamesByCcgid(@Param("ccgid") String ccgid);

    /**
     * Resolves a display name for a senior manager CCGID from the ACTIVE snapshot.
     *
     * @param ccgid senior manager CCGID
     * @return distinct senior manager names
     */
    @Query("""
            select distinct r.srManagerName
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.srManagerCcgid) = upper(:ccgid)
              and r.srManagerName is not null
            """)
    List<String> findSrManagerNamesByCcgid(@Param("ccgid") String ccgid);

    /**
     * Resolves a display name for a domain head CCGID from the ACTIVE snapshot.
     *
     * @param ccgid domain head CCGID
     * @return distinct domain head names
     */
    @Query("""
            select distinct r.domainHeadName
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.domainHeadCcgid) = upper(:ccgid)
              and r.domainHeadName is not null
            """)
    List<String> findDomainHeadNamesByCcgid(@Param("ccgid") String ccgid);

    interface HierarchyRow {
        String getSupervisorPositionId();

        String getCenter();

        String getDomain();

        String getPl1();

        String getPl2();

        String getPl3Code();

        String getPl3Name();
    }

    interface KpiAggregateRow {
        String getCarrier();

        String getSite();

        String getCustomerCountry();

        BigDecimal getDeliveryHc();
    }

    @Query("""
            select distinct r.supervisorPositionId as supervisorPositionId,
                   r.center as center,
                   r.domain as domain,
                   r.pl1 as pl1,
                   r.pl2 as pl2,
                   r.pl3Code as pl3Code,
                   r.pl3Name as pl3Name
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.supervisorCcgid) = upper(:ccgid)
              and r.supervisorPositionId is not null
            order by r.supervisorPositionId, r.center, r.domain, r.pl1, r.pl2, r.pl3Name
            """)
    List<HierarchyRow> findDistinctHierarchyBySupervisorCcgid(@Param("ccgid") String ccgid);

    @Query("""
            select distinct r.customerCountry
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and r.supervisorPositionId = :positionId
              and r.pl3Code = :pl3Code
              and r.customerCountry is not null
            order by r.customerCountry
            """)
    List<String> findDistinctCountries(
            @Param("positionId") String supervisorPositionId,
            @Param("pl3Code") String pl3Code);

    @Query("""
            select r.carrier as carrier,
                   r.site as site,
                   r.customerCountry as customerCountry,
                   sum(r.hc) as deliveryHc
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and r.supervisorPositionId = :positionId
              and r.pl3Code = :pl3Code
              and r.customerCountry in :countries
            group by r.carrier, r.site, r.customerCountry
            order by r.customerCountry, r.carrier, r.site
            """)
    List<KpiAggregateRow> aggregateKpis(
            @Param("positionId") String supervisorPositionId,
            @Param("pl3Code") String pl3Code,
            @Param("countries") List<String> countries);

    @Query("""
            select coalesce(sum(r.hc), 0)
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and r.supervisorPositionId = :positionId
              and r.pl3Code = :pl3Code
              and r.carrier = :carrier
              and r.site = :site
              and r.customerCountry = :country
            """)
    BigDecimal sumHeadcount(
            @Param("positionId") String supervisorPositionId,
            @Param("pl3Code") String pl3Code,
            @Param("carrier") String carrier,
            @Param("site") String site,
            @Param("country") String country);

    @Query("""
            select count(r) > 0
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.supervisorCcgid) = upper(:ccgid)
              and r.supervisorPositionId = :positionId
              and r.pl3Code = :pl3Code
            """)
    boolean existsActiveScopeForSupervisor(
            @Param("ccgid") String ccgid,
            @Param("positionId") String supervisorPositionId,
            @Param("pl3Code") String pl3Code);

    @Query("""
            select count(r) > 0
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.empCcgid) = upper(:ccgid)
              and r.supervisorPositionId = :positionId
              and r.pl3Code = :pl3Code
            """)
    boolean existsActiveScopeForAgent(
            @Param("ccgid") String ccgid,
            @Param("positionId") String supervisorPositionId,
            @Param("pl3Code") String pl3Code);

    /**
     * Lists distinct employees reporting to a supervisor in the ACTIVE snapshot.
     *
     * @param ccgid supervisor CCGID
     * @return agent rows ordered by name then CCGID
     */
    @Query("""
            select distinct r.empCcgid as empCcgid, r.empName as empName
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.supervisorCcgid) = upper(:ccgid)
              and r.empCcgid is not null
            order by r.empName, r.empCcgid
            """)
    List<TeamAgentRow> findDistinctAgentsBySupervisorCcgid(@Param("ccgid") String ccgid);

    /**
     * Lists Manager position ids currently occupied by this CCGID.
     *
     * @param ccgid senior manager CCGID
     * @return distinct sr_manager_position_id values
     */
    @Query("""
            select distinct r.srManagerPositionId
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.srManagerCcgid) = upper(:ccgid)
              and r.srManagerPositionId is not null
              and r.srManagerPositionId <> ''
            """)
    List<String> findSrManagerPositionIdsByCcgid(@Param("ccgid") String ccgid);

    /**
     * Lists CDH / Domain Head position ids currently occupied by this CCGID.
     *
     * @param ccgid domain head CCGID
     * @return distinct domain_head_position_id values
     */
    @Query("""
            select distinct r.domainHeadPositionId
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and upper(r.domainHeadCcgid) = upper(:ccgid)
              and r.domainHeadPositionId is not null
              and r.domainHeadPositionId <> ''
            """)
    List<String> findDomainHeadPositionIdsByCcgid(@Param("ccgid") String ccgid);

    /** Approver chain for a Supervisor position (Manager then CDH). */
    interface ApproverChainRow {
        String getSrManagerPositionId();

        String getSrManagerCcgid();

        String getSrManagerName();

        String getDomainHeadPositionId();

        String getDomainHeadCcgid();

        String getDomainHeadName();
    }

    /**
     * Resolves Manager and CDH positions for a Supervisor position from the ACTIVE snapshot.
     *
     * @param supervisorPositionId toolkit supervisor position
     * @return chain rows (hierarchy is unique per supervisor position)
     */
    @Query("""
            select r.srManagerPositionId as srManagerPositionId,
                   r.srManagerCcgid as srManagerCcgid,
                   r.srManagerName as srManagerName,
                   r.domainHeadPositionId as domainHeadPositionId,
                   r.domainHeadCcgid as domainHeadCcgid,
                   r.domainHeadName as domainHeadName
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and r.supervisorPositionId = :supervisorPositionId
              and r.srManagerPositionId is not null
              and r.srManagerPositionId <> ''
            """)
    List<ApproverChainRow> findApproverChainBySupervisorPositionId(
            @Param("supervisorPositionId") String supervisorPositionId);

    /** Current occupant of a Manager or CDH position. */
    interface OccupantRow {
        String getCcgid();

        String getName();
    }

    /**
     * Resolves the current occupant of a senior manager position.
     *
     * @param positionId sr_manager_position_id
     * @return occupant rows (hierarchy is unique per position)
     */
    @Query("""
            select distinct r.srManagerCcgid as ccgid, r.srManagerName as name
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and r.srManagerPositionId = :positionId
              and r.srManagerCcgid is not null
              and r.srManagerCcgid <> ''
            """)
    List<OccupantRow> findSrManagerOccupantByPositionId(@Param("positionId") String positionId);

    /**
     * Resolves the current occupant of a Domain Head / CDH position.
     *
     * @param positionId domain_head_position_id
     * @return occupant rows
     */
    @Query("""
            select distinct r.domainHeadCcgid as ccgid, r.domainHeadName as name
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and r.domainHeadPositionId = :positionId
              and r.domainHeadCcgid is not null
              and r.domainHeadCcgid <> ''
            """)
    List<OccupantRow> findDomainHeadOccupantByPositionId(@Param("positionId") String positionId);

    /** Projection for supervisor team agent filter options. */
    interface TeamAgentRow {
        String getEmpCcgid();

        String getEmpName();
    }

    /**
     * Distinct RST obligations in the ACTIVE snapshot: one Supervisor × PL3 per GBS Center.
     * Domain is the min name when a key spans more than one domain.
     *
     * @return obligation rows
     */
    @Query("""
            select r.center as center,
                   min(r.domain) as domain,
                   r.supervisorPositionId as supervisorPositionId,
                   r.pl3Code as pl3Code
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
              and r.center is not null
              and r.center <> ''
              and r.supervisorPositionId is not null
              and r.supervisorPositionId <> ''
              and r.pl3Code is not null
              and r.pl3Code <> ''
            group by r.center, r.supervisorPositionId, r.pl3Code
            order by r.center, r.supervisorPositionId, r.pl3Code
            """)
    List<DashboardObligationRow> findActiveDashboardObligations();

    /**
     * Sums Delivery HC across the ACTIVE Timesheet snapshot.
     *
     * @return total hc, or 0 when the snapshot is empty
     */
    @Query("""
            select coalesce(sum(r.hc), 0)
            from TimesheetSnapshotRow r
            where r.syncRun.status = 'ACTIVE'
            """)
    BigDecimal sumActiveHeadcount();

    /** One applicable dashboard unit from ACTIVE Timesheet. */
    interface DashboardObligationRow {
        String getCenter();

        String getDomain();

        String getSupervisorPositionId();

        String getPl3Code();
    }
}
