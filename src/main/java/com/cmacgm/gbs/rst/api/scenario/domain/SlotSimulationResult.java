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
     * Creates a real slot simulation result row.
     */
    public static SlotSimulationResult create(
            UUID simulationRunId,
            Instant slotStartAt,
            Instant slotEndAt,
            BigDecimal rawVolume,
            BigDecimal manualVolume,
            BigDecimal theoreticalFte,
            BigDecimal shiftFte,
            BigDecimal casesPerFte,
            BigDecimal teamCapacity,
            BigDecimal backlogStart,
            BigDecimal backlogEnd,
            BigDecimal volumeOutsideSla,
            BigDecimal tatResult,
            BigDecimal slaResult) {
        SlotSimulationResult row = new SlotSimulationResult();
        row.id = UUID.randomUUID();
        row.simulationRunId = simulationRunId;
        row.slotStartAt = slotStartAt;
        row.slotEndAt = slotEndAt;
        row.rawVolume = rawVolume;
        row.manualVolume = manualVolume;
        row.theoreticalFte = theoreticalFte;
        row.shiftFte = shiftFte;
        row.casesPerFte = casesPerFte;
        row.teamCapacity = teamCapacity;
        row.backlogStart = backlogStart;
        row.backlogEnd = backlogEnd;
        row.volumeOutsideSla = volumeOutsideSla;
        row.tatResult = tatResult;
        row.slaResult = slaResult;
        return row;
    }

    public UUID getId() { return id; }
    public UUID getSimulationRunId() { return simulationRunId; }
    public Instant getSlotStartAt() { return slotStartAt; }
    public Instant getSlotEndAt() { return slotEndAt; }
    public BigDecimal getRawVolume() { return rawVolume; }
    public BigDecimal getManualVolume() { return manualVolume; }
    public BigDecimal getTheoreticalFte() { return theoreticalFte; }
    public BigDecimal getShiftFte() { return shiftFte; }
    public BigDecimal getCasesPerFte() { return casesPerFte; }
    public BigDecimal getTeamCapacity() { return teamCapacity; }
    public BigDecimal getBacklogStart() { return backlogStart; }
    public BigDecimal getBacklogEnd() { return backlogEnd; }
    public BigDecimal getVolumeOutsideSla() { return volumeOutsideSla; }
    public BigDecimal getTatResult() { return tatResult; }
    public BigDecimal getSlaResult() { return slaResult; }
}
