package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Shift definition belonging to an Exercise Associated Data set. */
@Entity
@Table(name = "exercise_shift")
public class ExerciseShift {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "shift_no", nullable = false)
    private short shiftNo;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private long version;

    protected ExerciseShift() {
    }

    /**
     * Creates an active shift row for an Exercise.
     *
     * @param exerciseId owning Exercise
     * @param shiftNo shift number 1–5
     * @param startTime shift start
     * @param durationMinutes positive duration
     * @param headcount non-negative headcount
     * @param worksOnWeekend whether the shift covers weekends
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return new shift entity
     */
    public static ExerciseShift create(
            UUID exerciseId,
            short shiftNo,
            LocalTime startTime,
            int durationMinutes,
            BigDecimal headcount,
            boolean worksOnWeekend,
            UUID actorUserId,
            Instant now) {
        ExerciseShift shift = new ExerciseShift();
        shift.id = UUID.randomUUID();
        shift.exerciseId = exerciseId;
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

    /**
     * Updates shift fields in place (keeps id / shift_no).
     */
    public void replace(
            LocalTime startTime,
            int durationMinutes,
            BigDecimal headcount,
            boolean worksOnWeekend,
            UUID actorUserId,
            Instant now) {
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.headcount = headcount;
        this.worksOnWeekend = worksOnWeekend;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Soft-deletes this shift.
     *
     * @param actorUserId deleting Supervisor
     * @param now deletion timestamp
     */
    public void softDelete(UUID actorUserId, Instant now) {
        this.deletedAt = now;
        this.deletedBy = actorUserId;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public short getShiftNo() { return shiftNo; }
    public LocalTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getHeadcount() { return headcount; }
    public boolean isWorksOnWeekend() { return worksOnWeekend; }
    public Instant getDeletedAt() { return deletedAt; }
}
