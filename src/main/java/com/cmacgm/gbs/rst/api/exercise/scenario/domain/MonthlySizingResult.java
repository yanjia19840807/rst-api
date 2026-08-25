package com.cmacgm.gbs.rst.api.exercise.scenario.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** First day of the month (DATE). */
    @Column(nullable = false)
    private LocalDate month;

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
     * Creates a real monthly sizing result row.
     */
    public static MonthlySizingResult create(
            UUID simulationRunId,
            LocalDate month,
            BigDecimal forecastVolume,
            BigDecimal manualVolume,
            BigDecimal workdays,
            BigDecimal weekendDays,
            BigDecimal cycleTimeSeconds,
            BigDecimal nominalHcWithoutOt,
            BigDecimal nominalHcWithOt,
            BigDecimal productionSupportFte,
            BigDecimal rightSizingHc,
            BigDecimal capacityCreation) {
        MonthlySizingResult row = new MonthlySizingResult();
        row.id = UUID.randomUUID();
        row.simulationRunId = simulationRunId;
        row.month = month;
        row.forecastVolume = forecastVolume;
        row.manualVolume = manualVolume;
        row.workdays = workdays;
        row.weekendDays = weekendDays;
        row.cycleTimeSeconds = cycleTimeSeconds;
        row.nominalHcWithoutOt = nominalHcWithoutOt;
        row.nominalHcWithOt = nominalHcWithOt;
        row.productionSupportFte = productionSupportFte;
        row.rightSizingHc = rightSizingHc;
        row.capacityCreation = capacityCreation;
        return row;
    }

    public UUID getId() { return id; }
    public UUID getSimulationRunId() { return simulationRunId; }
    public LocalDate getMonth() { return month; }
    public BigDecimal getForecastVolume() { return forecastVolume; }
    public BigDecimal getManualVolume() { return manualVolume; }
    public BigDecimal getWorkdays() { return workdays; }
    public BigDecimal getWeekendDays() { return weekendDays; }
    public BigDecimal getCycleTimeSeconds() { return cycleTimeSeconds; }
    public BigDecimal getNominalHcWithoutOt() { return nominalHcWithoutOt; }
    public BigDecimal getNominalHcWithOt() { return nominalHcWithOt; }
    public BigDecimal getProductionSupportFte() { return productionSupportFte; }
    public BigDecimal getRightSizingHc() { return rightSizingHc; }
    public BigDecimal getCapacityCreation() { return capacityCreation; }
}
