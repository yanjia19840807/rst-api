package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Holiday entry belonging to an Exercise. */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false, length = 20)
    private HolidayDayKind holidayType;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

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
     * @param holidayType Holiday, Weekend, or Normal
     * @return new holiday entity
     */
    public static ExerciseHoliday create(
            UUID exerciseId,
            LocalDate holidayDate,
            String holidayName,
            HolidayDayKind holidayType) {
        if (holidayType == null) {
            throw new IllegalArgumentException("Holiday type is required.");
        }
        ExerciseHoliday holiday = new ExerciseHoliday();
        holiday.id = UUID.randomUUID();
        holiday.exerciseId = exerciseId;
        holiday.holidayDate = holidayDate;
        holiday.holidayName = holidayName;
        holiday.holidayType = holidayType;
        return holiday;
    }

    public void softDelete() {
        this.deleted = true;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayName() { return holidayName; }
    public HolidayDayKind getHolidayType() { return holidayType; }
    public boolean isDeleted() { return deleted; }
}
