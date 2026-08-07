package com.cmacgm.gbs.rst.api.exercise.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Immutable Shared KPI / Delivery HC line frozen with an Exercise.
 */
/**
 * Frozen Shared KPI / Delivery HC line captured when an Exercise is created.
 */
@Entity
@Table(name = "exercise_shared_kpi_line")
public class ExerciseSharedKpiLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private RstExercise exercise;

    @Column(name = "toolkit_shared_kpi_selection_id", nullable = false)
    private UUID toolkitSharedKpiSelectionId;

    @Column(name = "timesheet_sync_run_id", nullable = false)
    private UUID timesheetSyncRunId;

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

    @Column(nullable = false, length = 120)
    private String carrier;

    @Column(name = "customer_country", nullable = false, length = 120)
    private String customerCountry;

    @Column(name = "delivery_hc", nullable = false, precision = 18, scale = 6)
    private BigDecimal deliveryHc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected ExerciseSharedKpiLine() {
    }

    static ExerciseSharedKpiLine freeze(
            RstExercise exercise,
            UUID toolkitSharedKpiSelectionId,
            UUID timesheetSyncRunId,
            String center,
            String site,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            String carrier,
            String customerCountry,
            BigDecimal deliveryHc,
            UUID createdBy,
            Instant now) {
        ExerciseSharedKpiLine line = new ExerciseSharedKpiLine();
        line.id = UUID.randomUUID();
        line.exercise = exercise;
        line.toolkitSharedKpiSelectionId = toolkitSharedKpiSelectionId;
        line.timesheetSyncRunId = timesheetSyncRunId;
        line.center = center;
        line.site = site;
        line.domain = domain;
        line.pl1 = pl1;
        line.pl2 = pl2;
        line.pl3Code = pl3Code;
        line.pl3Name = pl3Name;
        line.carrier = carrier;
        line.customerCountry = customerCountry;
        line.deliveryHc = deliveryHc;
        line.createdAt = now;
        line.createdBy = createdBy;
        return line;
    }

    /**
     * @return Shared KPI line id
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return source Toolkit Shared KPI selection id
     */
    public UUID getToolkitSharedKpiSelectionId() {
        return toolkitSharedKpiSelectionId;
    }

    /**
     * @return Timesheet sync run used when freezing this line
     */
    public UUID getTimesheetSyncRunId() {
        return timesheetSyncRunId;
    }

    /**
     * @return center snapshot
     */
    public String getCenter() {
        return center;
    }

    /**
     * @return site snapshot
     */
    public String getSite() {
        return site;
    }

    /**
     * @return domain snapshot
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @return PL1 snapshot
     */
    public String getPl1() {
        return pl1;
    }

    /**
     * @return PL2 snapshot
     */
    public String getPl2() {
        return pl2;
    }

    /**
     * @return PL3 code snapshot
     */
    public String getPl3Code() {
        return pl3Code;
    }

    /**
     * @return PL3 name snapshot
     */
    public String getPl3Name() {
        return pl3Name;
    }

    /**
     * @return carrier snapshot
     */
    public String getCarrier() {
        return carrier;
    }

    /**
     * @return customer country snapshot
     */
    public String getCustomerCountry() {
        return customerCountry;
    }

    /**
     * @return delivery headcount from ACTIVE Timesheet at freeze time
     */
    public BigDecimal getDeliveryHc() {
        return deliveryHc;
    }
}
