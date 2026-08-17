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

    @Column(name = "source_template_line_id")
    private UUID sourceTemplateLineId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

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
     * @param actorCcgid creating Supervisor
     * @param now creation timestamp
     * @return new holiday entity
     */
    public static ExerciseHoliday create(
            UUID exerciseId,
            LocalDate holidayDate,
            String holidayName,
            String holidayType,
            String actorCcgid,
            Instant now) {
        ExerciseHoliday holiday = new ExerciseHoliday();
        holiday.id = UUID.randomUUID();
        holiday.exerciseId = exerciseId;
        holiday.holidayDate = holidayDate;
        holiday.holidayName = holidayName;
        holiday.holidayType = holidayType;
        holiday.createdAt = now;
        holiday.createdBy = actorCcgid;
        holiday.updatedAt = now;
        holiday.updatedBy = actorCcgid;
        return holiday;
    }

    /**
     * Creates a BASELINE holiday copied from a Center template line.
     */
    public static ExerciseHoliday createFromTemplate(
            UUID exerciseId,
            LocalDate holidayDate,
            String holidayName,
            UUID sourceTemplateLineId,
            String actorCcgid,
            Instant now) {
        ExerciseHoliday holiday = create(
                exerciseId, holidayDate, holidayName, "BASELINE", actorCcgid, now);
        holiday.sourceTemplateLineId = sourceTemplateLineId;
        return holiday;
    }

    /**
     * Soft-deletes this holiday.
     *
     * @param actorCcgid deleting Supervisor
     * @param now deletion timestamp
     */
    public void softDelete(String actorCcgid, Instant now) {
        this.deletedAt = now;
        this.deletedBy = actorCcgid;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayName() { return holidayName; }
    public String getHolidayType() { return holidayType; }
    public UUID getSourceTemplateLineId() { return sourceTemplateLineId; }
    public Instant getDeletedAt() { return deletedAt; }
}
