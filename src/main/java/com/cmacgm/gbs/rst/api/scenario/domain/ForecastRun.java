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
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "forecastRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ForecastPoint> points = new ArrayList<>();

    protected ForecastRun() {
    }

    /**
     * Creates an ACCEPTED MONTHLY forecast run shell (points added by caller).
     *
     * @param scenarioId owning scenario
     * @param runNo sequential run number
     * @param method forecast method name
     * @param methodVersion method version
     * @param trainingFrom training window start
     * @param trainingTo training window end
     * @param inputHash SHA-256 hex of request inputs
     * @param featureMetadata JSON metadata
     * @param actorCcgid creating user
     * @param now timestamp
     * @return accepted run without points
     */
    public static ForecastRun accepted(
            UUID scenarioId,
            int runNo,
            String method,
            String methodVersion,
            LocalDate trainingFrom,
            LocalDate trainingTo,
            String inputHash,
            String featureMetadata,
            String actorCcgid,
            Instant now) {
        return accepted(
                scenarioId,
                runNo,
                "MONTHLY",
                method,
                methodVersion,
                trainingFrom,
                trainingTo,
                inputHash,
                featureMetadata,
                actorCcgid,
                now);
    }

    /**
     * Creates an ACCEPTED forecast run shell (points added by caller).
     *
     * @param forecastLevel MONTHLY or DAILY
     */
    public static ForecastRun accepted(
            UUID scenarioId,
            int runNo,
            String forecastLevel,
            String method,
            String methodVersion,
            LocalDate trainingFrom,
            LocalDate trainingTo,
            String inputHash,
            String featureMetadata,
            String actorCcgid,
            Instant now) {
        ForecastRun run = new ForecastRun();
        run.id = UUID.randomUUID();
        run.scenarioId = scenarioId;
        run.runNo = runNo;
        run.forecastLevel = forecastLevel == null || forecastLevel.isBlank() ? "MONTHLY" : forecastLevel;
        run.method = method;
        run.methodVersion = methodVersion;
        run.trainingFrom = trainingFrom;
        run.trainingTo = trainingTo;
        run.inputHash = inputHash;
        run.featureMetadata = featureMetadata;
        run.status = "ACCEPTED";
        run.startedAt = now;
        run.completedAt = now;
        run.createdBy = actorCcgid;
        run.createdAt = now;
        return run;
    }

    /**
     * Adds a point to this run (cascade persist).
     *
     * @param point forecast point
     */
    public void addPoint(ForecastPoint point) {
        this.points.add(point);
    }

    public UUID getId() { return id; }
    public UUID getScenarioId() { return scenarioId; }
    public int getRunNo() { return runNo; }
    public String getForecastLevel() { return forecastLevel; }
    public String getMethod() { return method; }
    public String getMethodVersion() { return methodVersion; }
    public LocalDate getTrainingFrom() { return trainingFrom; }
    public LocalDate getTrainingTo() { return trainingTo; }
    public String getFeatureMetadata() { return featureMetadata; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<ForecastPoint> getPoints() { return Collections.unmodifiableList(points); }
}
