package com.cmacgm.gbs.rst.api.toolkit.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseHoliday;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Latest approved holiday row for a Toolkit. */
@Entity
@Table(name = "toolkit_holiday")
public class ToolkitHoliday {

    @Id
    private UUID id;

    @Column(name = "toolkit_id", nullable = false)
    private UUID toolkitId;

    @Column(name = "source_exercise_id", nullable = false)
    private UUID sourceExerciseId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, length = 200)
    private String holidayName;

    @Column(name = "holiday_type", nullable = false, length = 20)
    private String holidayType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    protected ToolkitHoliday() {
    }

    /**
     * Snapshots one Exercise holiday onto a Toolkit.
     */
    public static ToolkitHoliday fromExercise(
            UUID toolkitId,
            UUID sourceExerciseId,
            ExerciseHoliday source,
            String actorCcgid,
            Instant now) {
        ToolkitHoliday holiday = new ToolkitHoliday();
        holiday.id = UUID.randomUUID();
        holiday.toolkitId = toolkitId;
        holiday.sourceExerciseId = sourceExerciseId;
        holiday.holidayDate = source.getHolidayDate();
        holiday.holidayName = source.getHolidayName();
        holiday.holidayType = source.getHolidayType();
        holiday.createdAt = now;
        holiday.createdBy = actorCcgid;
        holiday.updatedAt = now;
        holiday.updatedBy = actorCcgid;
        return holiday;
    }

    public UUID getId() { return id; }
    public UUID getToolkitId() { return toolkitId; }
    public UUID getSourceExerciseId() { return sourceExerciseId; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayName() { return holidayName; }
    public String getHolidayType() { return holidayType; }
}
