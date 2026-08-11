package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Forecast point belonging to a forecast run. */
@Entity
@Table(name = "forecast_point")
public class ForecastPoint {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forecast_run_id", nullable = false)
    private ForecastRun forecastRun;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "forecast_mean", nullable = false, precision = 24, scale = 6)
    private BigDecimal forecastMean;

    @Column(name = "lower_bound", precision = 24, scale = 6)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", precision = 24, scale = 6)
    private BigDecimal upperBound;

    @Column(name = "accepted_value", precision = 24, scale = 6)
    private BigDecimal acceptedValue;

    @Column(name = "override_reason")
    private String overrideReason;

    protected ForecastPoint() {
    }

    /**
     * Creates a forecast point for a monthly period.
     *
     * @param run owning run
     * @param start period start (month start)
     * @param end period end (month end)
     * @param mean forecast mean
     * @param lower lower bound
     * @param upper upper bound
     * @return point
     */
    public static ForecastPoint create(
            ForecastRun run,
            LocalDate start,
            LocalDate end,
            BigDecimal mean,
            BigDecimal lower,
            BigDecimal upper) {
        ForecastPoint point = new ForecastPoint();
        point.id = UUID.randomUUID();
        point.forecastRun = run;
        point.periodStart = start;
        point.periodEnd = end;
        point.forecastMean = mean;
        point.lowerBound = lower;
        point.upperBound = upper;
        point.acceptedValue = mean;
        return point;
    }

    public UUID getId() { return id; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getForecastMean() { return forecastMean; }
    public BigDecimal getLowerBound() { return lowerBound; }
    public BigDecimal getUpperBound() { return upperBound; }
    public BigDecimal getAcceptedValue() { return acceptedValue; }
}
