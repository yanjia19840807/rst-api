package com.cmacgm.gbs.rst.api.scenario.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Simulation run (monthly sizing / daily / slot) for a Scenario. */
@Entity
@Table(name = "simulation_run")
public class SimulationRun {

    @Id
    private UUID id;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "forecast_run_id")
    private UUID forecastRunId;

    @Column(name = "run_type", nullable = false, length = 20)
    private String runType;

    @Column(name = "run_no", nullable = false)
    private int runNo;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "calculation_version", nullable = false, length = 80)
    private String calculationVersion;

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson;

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

    protected SimulationRun() {
    }

    /**
     * Creates an ACCEPTED stub simulation run.
     *
     * @param scenarioId owning scenario
     * @param forecastRunId optional forecast reference
     * @param runType MONTHLY_SIZING / DAILY / SLOT
     * @param runNo sequential number per type
     * @param actorUserId creating user
     * @param now timestamp
     * @return accepted stub run
     */
    public static SimulationRun stubAccepted(
            UUID scenarioId,
            UUID forecastRunId,
            String runType,
            int runNo,
            UUID actorUserId,
            Instant now) {
        SimulationRun run = new SimulationRun();
        run.id = UUID.randomUUID();
        run.scenarioId = scenarioId;
        run.forecastRunId = forecastRunId;
        run.runType = runType;
        run.runNo = runNo;
        String hashSeed = ("stub-sim-" + runType + "-" + scenarioId + "-" + runNo).replace("-", "");
        run.inputHash = (hashSeed + "0".repeat(64)).substring(0, 64);
        run.calculationVersion = "stub-v1";
        run.status = "ACCEPTED";
        run.summaryJson = "{\"stub\":true,\"runType\":\"" + runType + "\"}";
        run.startedAt = now;
        run.completedAt = now;
        run.createdBy = actorUserId;
        run.createdAt = now;
        return run;
    }

    public UUID getId() { return id; }
    public UUID getScenarioId() { return scenarioId; }
    public UUID getForecastRunId() { return forecastRunId; }
    public String getRunType() { return runType; }
    public int getRunNo() { return runNo; }
    public String getStatus() { return status; }
    public String getSummaryJson() { return summaryJson; }
}
