package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One denormalized Timesheet detail row belonging to a sync run.
 */
@Entity
@Table(name = "timesheet_snapshot_row")
public class TimesheetSnapshotRow {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_run_id", nullable = false)
    private TimesheetSyncRun syncRun;

    @Column(name = "emp_ccgid", nullable = false, length = 32)
    private String empCcgid;
    @Column(name = "emp_name", nullable = false, length = 200)
    private String empName;
    @Column(name = "emp_position_id", nullable = false, length = 80)
    private String empPositionId;
    @Column(name = "supervisor_ccgid", length = 32)
    private String supervisorCcgid;
    @Column(name = "supervisor_name", length = 200)
    private String supervisorName;
    @Column(name = "supervisor_position_id", length = 80)
    private String supervisorPositionId;
    @Column(name = "sr_manager_ccgid", length = 32)
    private String srManagerCcgid;
    @Column(name = "sr_manager_name", length = 200)
    private String srManagerName;
    @Column(name = "sr_manager_position_id", length = 80)
    private String srManagerPositionId;
    @Column(name = "domain_head_ccgid", length = 32)
    private String domainHeadCcgid;
    @Column(name = "domain_head_name", length = 200)
    private String domainHeadName;
    @Column(name = "domain_head_position_id", length = 80)
    private String domainHeadPositionId;
    @Column(nullable = false, length = 120)
    private String center;
    @Column(nullable = false, length = 80)
    private String site;
    @Column(nullable = false, length = 120)
    private String domain;
    @Column(nullable = false, length = 200)
    private String pl1;
    @Column(nullable = false, length = 200)
    private String pl2;
    @Column(name = "pl3_code", nullable = false, length = 80)
    private String pl3Code;
    @Column(name = "pl3_name", nullable = false, length = 200)
    private String pl3Name;
    @Column(length = 120)
    private String carrier;
    @Column(name = "customer_country", length = 120)
    private String customerCountry;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal hc;

    protected TimesheetSnapshotRow() {
    }

    /**
     * Creates a snapshot row attached to the given sync run.
     *
     * @param syncRun owning sync run
     * @param empCcgid employee CCGID (already normalized)
     * @param empName employee display name
     * @param empPositionId employee position id
     * @param supervisorCcgid supervisor CCGID
     * @param supervisorName supervisor display name
     * @param supervisorPositionId supervisor position id
     * @param srManagerCcgid senior manager CCGID
     * @param srManagerName senior manager display name
     * @param srManagerPositionId senior manager position id
     * @param domainHeadCcgid domain head CCGID
     * @param domainHeadName domain head display name
     * @param domainHeadPositionId domain head position id
     * @param center GBS center
     * @param site site code
     * @param domain business domain
     * @param pl1 process level 1
     * @param pl2 process level 2
     * @param pl3Code process level 3 code
     * @param pl3Name process level 3 name
     * @param carrier carrier
     * @param customerCountry customer country
     * @param hc headcount contribution
     * @return persisted-ready snapshot row
     */
    public static TimesheetSnapshotRow create(
            TimesheetSyncRun syncRun,
            String empCcgid,
            String empName,
            String empPositionId,
            String supervisorCcgid,
            String supervisorName,
            String supervisorPositionId,
            String srManagerCcgid,
            String srManagerName,
            String srManagerPositionId,
            String domainHeadCcgid,
            String domainHeadName,
            String domainHeadPositionId,
            String center,
            String site,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            String carrier,
            String customerCountry,
            BigDecimal hc) {
        TimesheetSnapshotRow row = new TimesheetSnapshotRow();
        row.id = UUID.randomUUID();
        row.syncRun = syncRun;
        row.empCcgid = empCcgid;
        row.empName = empName;
        row.empPositionId = empPositionId;
        row.supervisorCcgid = supervisorCcgid;
        row.supervisorName = supervisorName;
        row.supervisorPositionId = supervisorPositionId;
        row.srManagerCcgid = srManagerCcgid;
        row.srManagerName = srManagerName;
        row.srManagerPositionId = srManagerPositionId;
        row.domainHeadCcgid = domainHeadCcgid;
        row.domainHeadName = domainHeadName;
        row.domainHeadPositionId = domainHeadPositionId;
        row.center = center;
        row.site = site;
        row.domain = domain;
        row.pl1 = pl1;
        row.pl2 = pl2;
        row.pl3Code = pl3Code;
        row.pl3Name = pl3Name;
        row.carrier = carrier;
        row.customerCountry = customerCountry;
        row.hc = hc;
        return row;
    }

    public UUID getId() {
        return id;
    }

    public TimesheetSyncRun getSyncRun() {
        return syncRun;
    }

    public String getEmpCcgid() {
        return empCcgid;
    }

    public String getEmpName() {
        return empName;
    }

    public String getEmpPositionId() {
        return empPositionId;
    }

    public String getSupervisorCcgid() {
        return supervisorCcgid;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public String getSupervisorPositionId() {
        return supervisorPositionId;
    }

    public String getSrManagerCcgid() {
        return srManagerCcgid;
    }

    public String getSrManagerName() {
        return srManagerName;
    }

    public String getSrManagerPositionId() {
        return srManagerPositionId;
    }

    public String getDomainHeadCcgid() {
        return domainHeadCcgid;
    }

    public String getDomainHeadName() {
        return domainHeadName;
    }

    public String getDomainHeadPositionId() {
        return domainHeadPositionId;
    }

    public String getCenter() {
        return center;
    }

    public String getSite() {
        return site;
    }

    public String getDomain() {
        return domain;
    }

    public String getPl1() {
        return pl1;
    }

    public String getPl2() {
        return pl2;
    }

    public String getPl3Code() {
        return pl3Code;
    }

    public String getPl3Name() {
        return pl3Name;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getCustomerCountry() {
        return customerCountry;
    }

    public BigDecimal getHc() {
        return hc;
    }
}
