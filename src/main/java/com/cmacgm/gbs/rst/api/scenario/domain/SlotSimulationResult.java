package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Immutable slot simulation result row. */
@Entity
@Table(name = "slot_simulation_result")
public class SlotSimulationResult {

    @Id
    private UUID id;

    @Column(name = "simulation_run_id", nullable = false)
    private UUID simulationRunId;

    @Column(name = "slot_start_at", nullable = false)
    private Instant slotStartAt;

    @Column(name = "slot_end_at", nullable = false)
    private Instant slotEndAt;

    @Column(name = "raw_volume", precision = 24, scale = 6)
    private BigDecimal rawVolume;

    @Column(name = "manual_volume", precision = 24, scale = 6)
    private BigDecimal manualVolume;

    @Column(name = "theoretical_fte", precision = 18, scale = 6)
    private BigDecimal theoreticalFte;

    @Column(name = "shift_fte", precision = 18, scale = 6)
    private BigDecimal shiftFte;

    @Column(name = "cases_per_fte", precision = 18, scale = 6)
    private BigDecimal casesPerFte;

    @Column(name = "team_capacity", precision = 18, scale = 6)
    private BigDecimal teamCapacity;

    @Column(name = "backlog_start", precision = 24, scale = 6)
    private BigDecimal backlogStart;

    @Column(name = "backlog_end", precision = 24, scale = 6)
    private BigDecimal backlogEnd;

    @Column(name = "volume_outside_sla", precision = 24, scale = 6)
    private BigDecimal volumeOutsideSla;

    @Column(name = "tat_result", precision = 18, scale = 6)
    private BigDecimal tatResult;

    @Column(name = "sla_result", precision = 12, scale = 8)
    private BigDecimal slaResult;

    protected SlotSimulationResult() {
    }

    /**
     * Creates a stub slot simulation result for Official readiness.
     *
     * @param simulationRunId parent ACCEPTED SLOT run
     * @param slotStartAt slot start
     * @param slotEndAt slot end
     * @return stub result row
     */
    public static SlotSimulationResult stub(UUID simulationRunId, Instant slotStartAt, Instant slotEndAt) {
        SlotSimulationResult row = new SlotSimulationResult();
        row.id = UUID.randomUUID();
        row.simulationRunId = simulationRunId;
        row.slotStartAt = slotStartAt;
        row.slotEndAt = slotEndAt;
        row.rawVolume = new BigDecimal("50.000000");
        row.theoreticalFte = new BigDecimal("2.000000");
        row.shiftFte = new BigDecimal("2.000000");
        row.casesPerFte = new BigDecimal("25.000000");
        row.teamCapacity = new BigDecimal("50.000000");
        row.backlogStart = BigDecimal.ZERO;
        row.backlogEnd = BigDecimal.ZERO;
        row.volumeOutsideSla = BigDecimal.ZERO;
        row.slaResult = new BigDecimal("0.98000000");
        return row;
    }

    public UUID getId() { return id; }
    public UUID getSimulationRunId() { return simulationRunId; }
}
