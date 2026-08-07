package com.cmacgm.gbs.rst.api.scenario.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Forecast run belonging to a Scenario. */
@Entity
@Table(name = "forecast_run")
public class ForecastRun {

    @Id
    private UUID id;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "run_no", nullable = false)
    private int runNo;

    @Column(name = "forecast_level", nullable = false, length = 20)
    private String forecastLevel;

    @Column(nullable = false, length = 30)
    private String method;

    @Column(name = "method_version", nullable = false, length = 80)
    private String methodVersion;

    @Column(name = "training_from", nullable = false)
    private LocalDate trainingFrom;

    @Column(name = "training_to", nullable = false)
    private LocalDate trainingTo;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_metadata", columnDefinition = "jsonb")
    private String featureMetadata;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "forecastRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ForecastPoint> points = new ArrayList<>();

    protected ForecastRun() {
    }

    /**
     * Creates an ACCEPTED stub forecast run with one monthly point.
     *
     * @param scenarioId owning scenario
     * @param runNo sequential run number
     * @param sizingMonth YYYY-MM used as forecast period
     * @param actorUserId creating user
     * @param now timestamp
     * @return accepted stub forecast
     */
    public static ForecastRun stubAccepted(
            UUID scenarioId, int runNo, String sizingMonth, UUID actorUserId, Instant now) {
        ForecastRun run = new ForecastRun();
        run.id = UUID.randomUUID();
        run.scenarioId = scenarioId;
        run.runNo = runNo;
        run.forecastLevel = "MONTHLY";
        run.method = "STUB";
        run.methodVersion = "stub-v1";
        LocalDate monthStart = LocalDate.parse(sizingMonth + "-01");
        run.trainingFrom = monthStart.minusMonths(12);
        run.trainingTo = monthStart.minusDays(1);
        String hashSeed = ("stub-forecast-" + scenarioId + "-" + runNo).replace("-", "");
        run.inputHash = (hashSeed + "0".repeat(64)).substring(0, 64);
        run.featureMetadata = "{\"stub\":true}";
        run.status = "ACCEPTED";
        run.startedAt = now;
        run.completedAt = now;
        run.createdBy = actorUserId;
        run.createdAt = now;
        ForecastPoint point = ForecastPoint.stub(run, monthStart, monthStart.withDayOfMonth(monthStart.lengthOfMonth()), now);
        run.points.add(point);
        return run;
    }

    public UUID getId() { return id; }
    public UUID getScenarioId() { return scenarioId; }
    public int getRunNo() { return runNo; }
    public String getForecastLevel() { return forecastLevel; }
    public String getMethod() { return method; }
    public String getMethodVersion() { return methodVersion; }
    public String getStatus() { return status; }
    public List<ForecastPoint> getPoints() { return Collections.unmodifiableList(points); }
}
