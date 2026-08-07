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

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(length = 64)
    private String timezone;

    @Column(name = "weekend_code", length = 40)
    private String weekendCode;

    @Column(name = "baseline_source", length = 80)
    private String baselineSource;

    @Column(name = "baseline_version", length = 40)
    private String baselineVersion;

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

    protected ExerciseCalendar() {
    }

    /**
     * Creates an empty calendar shell for a newly created Exercise.
     *
     * @param exerciseId owning Exercise id
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return empty calendar ready for later PUT edits
     */
    public static ExerciseCalendar emptyShell(UUID exerciseId, UUID actorUserId, Instant now) {
        ExerciseCalendar calendar = new ExerciseCalendar();
        calendar.exerciseId = exerciseId;
        calendar.createdAt = now;
        calendar.createdBy = actorUserId;
        calendar.updatedAt = now;
        calendar.updatedBy = actorUserId;
        return calendar;
    }

    /**
     * Replaces calendar header fields.
     *
     * @param countryCode ISO-like country code
     * @param timezone IANA timezone id
     * @param weekendCode weekend pattern code
     * @param baselineSource optional baseline catalogue source
     * @param baselineVersion optional baseline catalogue version
     * @param actorUserId updating Supervisor
     * @param now update timestamp
     */
    public void replace(
            String countryCode,
            String timezone,
            String weekendCode,
            String baselineSource,
            String baselineVersion,
            UUID actorUserId,
            Instant now) {
        this.countryCode = countryCode;
        this.timezone = timezone;
        this.weekendCode = weekendCode;
        this.baselineSource = baselineSource;
        this.baselineVersion = baselineVersion;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    public UUID getExerciseId() { return exerciseId; }
    public String getCountryCode() { return countryCode; }
    public String getTimezone() { return timezone; }
    public String getWeekendCode() { return weekendCode; }
    public String getBaselineSource() { return baselineSource; }
    public String getBaselineVersion() { return baselineVersion; }
    public long getVersion() { return version; }
}
