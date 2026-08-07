package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Holiday entry belonging to an Exercise calendar. */
@Entity
@Table(name = "exercise_holiday")
public class ExerciseHoliday {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, length = 200)
    private String holidayName;

    @Column(name = "holiday_type", nullable = false, length = 20)
    private String holidayType;

    @Column(name = "is_working_day_override")
    private Boolean workingDayOverride;

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

    protected ExerciseHoliday() {
    }

    /**
     * Creates a holiday row for an Exercise calendar.
     *
     * @param exerciseId owning Exercise
     * @param holidayDate calendar date
     * @param holidayName display name
     * @param holidayType BASELINE or CUSTOM
     * @param workingDayOverride optional working-day override
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return new holiday entity
     */
    public static ExerciseHoliday create(
            UUID exerciseId,
            LocalDate holidayDate,
            String holidayName,
            String holidayType,
            Boolean workingDayOverride,
            UUID actorUserId,
            Instant now) {
        ExerciseHoliday holiday = new ExerciseHoliday();
        holiday.id = UUID.randomUUID();
        holiday.exerciseId = exerciseId;
        holiday.holidayDate = holidayDate;
        holiday.holidayName = holidayName;
        holiday.holidayType = holidayType;
        holiday.workingDayOverride = workingDayOverride;
        holiday.createdAt = now;
        holiday.createdBy = actorUserId;
        holiday.updatedAt = now;
        holiday.updatedBy = actorUserId;
        return holiday;
    }

    /**
     * Soft-deletes this holiday.
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
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayName() { return holidayName; }
    public String getHolidayType() { return holidayType; }
    public Boolean getWorkingDayOverride() { return workingDayOverride; }
    public Instant getDeletedAt() { return deletedAt; }
}
