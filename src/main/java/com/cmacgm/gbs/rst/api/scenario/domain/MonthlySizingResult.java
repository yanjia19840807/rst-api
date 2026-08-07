package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Immutable monthly sizing result row. */
@Entity
@Table(name = "monthly_sizing_result")
public class MonthlySizingResult {

    @Id
    private UUID id;

    @Column(name = "simulation_run_id", nullable = false)
    private UUID simulationRunId;

    @Column(nullable = false, length = 7)
    private String month;

    @Column(name = "forecast_volume", precision = 24, scale = 6)
    private BigDecimal forecastVolume;

    @Column(name = "manual_volume", precision = 24, scale = 6)
    private BigDecimal manualVolume;

    @Column(precision = 18, scale = 6)
    private BigDecimal workdays;

    @Column(name = "weekend_days", precision = 18, scale = 6)
    private BigDecimal weekendDays;

    @Column(name = "cycle_time_seconds", precision = 18, scale = 6)
    private BigDecimal cycleTimeSeconds;

    @Column(name = "nominal_hc_without_ot", precision = 18, scale = 6)
    private BigDecimal nominalHcWithoutOt;

    @Column(name = "nominal_hc_with_ot", precision = 18, scale = 6)
    private BigDecimal nominalHcWithOt;

    @Column(name = "production_support_fte", precision = 18, scale = 6)
    private BigDecimal productionSupportFte;

    @Column(name = "right_sizing_hc", precision = 18, scale = 6)
    private BigDecimal rightSizingHc;

    @Column(name = "capacity_creation", precision = 18, scale = 6)
    private BigDecimal capacityCreation;

    protected MonthlySizingResult() {
    }

    /**
     * Creates a stub monthly sizing result for Official readiness.
     *
     * @param simulationRunId parent ACCEPTED MONTHLY_SIZING run
     * @param month YYYY-MM
     * @return stub result row
     */
    public static MonthlySizingResult stub(UUID simulationRunId, String month) {
        MonthlySizingResult row = new MonthlySizingResult();
        row.id = UUID.randomUUID();
        row.simulationRunId = simulationRunId;
        row.month = month;
        row.forecastVolume = new BigDecimal("1000.000000");
        row.workdays = new BigDecimal("22");
        row.weekendDays = new BigDecimal("8");
        row.cycleTimeSeconds = new BigDecimal("120.000000");
        row.nominalHcWithoutOt = new BigDecimal("10.000000");
        row.nominalHcWithOt = new BigDecimal("9.500000");
        row.productionSupportFte = new BigDecimal("0.500000");
        row.rightSizingHc = new BigDecimal("10.500000");
        row.capacityCreation = new BigDecimal("0.250000");
        return row;
    }

    public UUID getId() { return id; }
    public UUID getSimulationRunId() { return simulationRunId; }
    public String getMonth() { return month; }
    public BigDecimal getRightSizingHc() { return rightSizingHc; }
}
