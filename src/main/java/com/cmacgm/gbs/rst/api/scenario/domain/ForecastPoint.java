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

    static ForecastPoint stub(ForecastRun run, LocalDate start, LocalDate end, java.time.Instant ignored) {
        ForecastPoint point = new ForecastPoint();
        point.id = UUID.randomUUID();
        point.forecastRun = run;
        point.periodStart = start;
        point.periodEnd = end;
        point.forecastMean = new BigDecimal("1000.000000");
        point.lowerBound = new BigDecimal("900.000000");
        point.upperBound = new BigDecimal("1100.000000");
        point.acceptedValue = point.forecastMean;
        return point;
    }

    public UUID getId() { return id; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getForecastMean() { return forecastMean; }
    public BigDecimal getAcceptedValue() { return acceptedValue; }
}
