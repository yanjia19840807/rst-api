package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Immutable daily simulation result row. */
@Entity
@Table(name = "daily_simulation_result")
public class DailySimulationResult {

    @Id
    private UUID id;

    @Column(name = "simulation_run_id", nullable = false)
    private UUID simulationRunId;

    @Column(name = "result_date", nullable = false)
    private LocalDate resultDate;

    @Column(name = "forecast_volume", precision = 24, scale = 6)
    private BigDecimal forecastVolume;

    @Column(name = "manual_volume", precision = 24, scale = 6)
    private BigDecimal manualVolume;

    @Column(name = "is_holiday")
    private Boolean holiday;

    @Column(name = "is_working_day")
    private Boolean workingDay;

    @Column(name = "simulation_hc", precision = 18, scale = 6)
    private BigDecimal simulationHc;

    @Column(name = "standard_capacity", precision = 18, scale = 6)
    private BigDecimal standardCapacity;

    @Column(name = "overtime_capacity", precision = 18, scale = 6)
    private BigDecimal overtimeCapacity;

    @Column(name = "backlog_start", precision = 24, scale = 6)
    private BigDecimal backlogStart;

    @Column(name = "backlog_end", precision = 24, scale = 6)
    private BigDecimal backlogEnd;

    @Column(name = "sla_output", precision = 12, scale = 8)
    private BigDecimal slaOutput;

    protected DailySimulationResult() {
    }

    /**
     * Creates a real daily simulation result row.
     */
    public static DailySimulationResult create(
            UUID simulationRunId,
            LocalDate resultDate,
            BigDecimal forecastVolume,
            BigDecimal manualVolume,
            boolean holiday,
            boolean workingDay,
            BigDecimal simulationHc,
            BigDecimal standardCapacity,
            BigDecimal overtimeCapacity,
            BigDecimal backlogStart,
            BigDecimal backlogEnd) {
        DailySimulationResult row = new DailySimulationResult();
        row.id = UUID.randomUUID();
        row.simulationRunId = simulationRunId;
        row.resultDate = resultDate;
        row.forecastVolume = forecastVolume;
        row.manualVolume = manualVolume;
        row.holiday = holiday;
        row.workingDay = workingDay;
        row.simulationHc = simulationHc;
        row.standardCapacity = standardCapacity;
        row.overtimeCapacity = overtimeCapacity;
        row.backlogStart = backlogStart;
        row.backlogEnd = backlogEnd;
        row.slaOutput = null;
        return row;
    }

    public UUID getId() { return id; }
    public UUID getSimulationRunId() { return simulationRunId; }
    public LocalDate getResultDate() { return resultDate; }
    public BigDecimal getForecastVolume() { return forecastVolume; }
    public BigDecimal getManualVolume() { return manualVolume; }
    public Boolean getHoliday() { return holiday; }
    public Boolean getWorkingDay() { return workingDay; }
    public BigDecimal getSimulationHc() { return simulationHc; }
    public BigDecimal getStandardCapacity() { return standardCapacity; }
    public BigDecimal getOvertimeCapacity() { return overtimeCapacity; }
    public BigDecimal getBacklogStart() { return backlogStart; }
    public BigDecimal getBacklogEnd() { return backlogEnd; }
    public BigDecimal getSlaOutput() { return slaOutput; }
}
