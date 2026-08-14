package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One-to-one Associated Data calendar header for an Exercise.
 */
@Entity
@Table(name = "exercise_calendar")
public class ExerciseCalendar {

    @Id
    @Column(name = "exercise_id")
    private UUID exerciseId;

    @Column(name = "weekend_code", length = 40)
    private String weekendCode;

    @Column(name = "baseline_source", length = 80)
    private String baselineSource;

    @Column(name = "baseline_version", length = 40)
    private String baselineVersion;

    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    @Column(name = "source_template_version")
    private Integer sourceTemplateVersion;

    @Column(name = "baseline_year")
    private Short baselineYear;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    private long version;

    protected ExerciseCalendar() {
    }

    /**
     * Creates an empty calendar shell for a newly created Exercise.
     */
    public static ExerciseCalendar emptyShell(UUID exerciseId, String actorCcgid, Instant now) {
        ExerciseCalendar calendar = new ExerciseCalendar();
        calendar.exerciseId = exerciseId;
        calendar.createdAt = now;
        calendar.createdBy = actorCcgid;
        calendar.updatedAt = now;
        calendar.updatedBy = actorCcgid;
        return calendar;
    }

    /**
     * Replaces calendar header fields editable by Supervisor.
     */
    public void replace(
            String weekendCode,
            String baselineSource,
            String baselineVersion,
            String actorCcgid,
            Instant now) {
        this.weekendCode = weekendCode;
        if (baselineSource != null) {
            this.baselineSource = baselineSource;
        }
        if (baselineVersion != null) {
            this.baselineVersion = baselineVersion;
        }
        touch(actorCcgid, now);
    }

    /**
     * Applies metadata copied from a Center holiday template (or no-template defaults).
     */
    public void applyTemplateMeta(
            String weekendCode,
            UUID sourceTemplateId,
            Integer sourceTemplateVersion,
            Short baselineYear,
            String baselineSource,
            String baselineVersion,
            String actorCcgid,
            Instant now) {
        this.weekendCode = weekendCode;
        this.sourceTemplateId = sourceTemplateId;
        this.sourceTemplateVersion = sourceTemplateVersion;
        this.baselineYear = baselineYear;
        this.baselineSource = baselineSource;
        this.baselineVersion = baselineVersion;
        touch(actorCcgid, now);
    }

    public void touch(String actorCcgid, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    public UUID getExerciseId() { return exerciseId; }
    public String getWeekendCode() { return weekendCode; }
    public String getBaselineSource() { return baselineSource; }
    public String getBaselineVersion() { return baselineVersion; }
    public UUID getSourceTemplateId() { return sourceTemplateId; }
    public Integer getSourceTemplateVersion() { return sourceTemplateVersion; }
    public Short getBaselineYear() { return baselineYear; }
    public long getVersion() { return version; }
}
