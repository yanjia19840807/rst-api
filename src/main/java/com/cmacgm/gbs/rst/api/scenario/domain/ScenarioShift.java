package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Slot Simulation shift input belonging to a Scenario. */
@Entity
@Table(name = "scenario_shift")
public class ScenarioShift {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "shift_no", nullable = false)
    private short shiftNo;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false, precision = 18, scale = 6)
    private BigDecimal durationMinutes;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal headcount;

    @Column(name = "works_on_weekend", nullable = false)
    private boolean worksOnWeekend;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;

    protected ScenarioShift() {
    }

    /**
     * Creates a detached shift row until attached to a Scenario.
     */
    public static ScenarioShift create(
            short shiftNo,
            LocalTime startTime,
            BigDecimal durationMinutes,
            BigDecimal headcount,
            boolean worksOnWeekend,
            UUID actorUserId,
            Instant now) {
        ScenarioShift shift = new ScenarioShift();
        shift.id = UUID.randomUUID();
        shift.shiftNo = shiftNo;
        shift.startTime = startTime;
        shift.durationMinutes = durationMinutes;
        shift.headcount = headcount;
        shift.worksOnWeekend = worksOnWeekend;
        shift.createdAt = now;
        shift.createdBy = actorUserId;
        shift.updatedAt = now;
        shift.updatedBy = actorUserId;
        return shift;
    }

    void attach(Scenario scenario) {
        this.scenario = scenario;
    }

    /**
     * Copies editable fields (keeps this row's id for unique-key upserts).
     */
    void overwriteValues(ScenarioShift source, UUID actorUserId, Instant now) {
        this.shiftNo = source.shiftNo;
        this.startTime = source.startTime;
        this.durationMinutes = source.durationMinutes;
        this.headcount = source.headcount;
        this.worksOnWeekend = source.worksOnWeekend;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    public UUID getId() { return id; }
    public short getShiftNo() { return shiftNo; }
    public LocalTime getStartTime() { return startTime; }
    public BigDecimal getDurationMinutes() { return durationMinutes; }
    public BigDecimal getHeadcount() { return headcount; }
    public boolean isWorksOnWeekend() { return worksOnWeekend; }
}
