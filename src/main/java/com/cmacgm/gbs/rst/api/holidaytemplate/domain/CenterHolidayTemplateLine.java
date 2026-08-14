package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One holiday date row in a Center holiday template. */
@Entity
@Table(name = "center_holiday_template_line")
public class CenterHolidayTemplateLine {

    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, length = 200)
    private String holidayName;

    @Column(name = "is_working_day_override")
    private Boolean workingDayOverride;

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
    @Column(name = "row_version")
    private long rowVersion;

    protected CenterHolidayTemplateLine() {
    }

    public static CenterHolidayTemplateLine create(
            UUID templateId,
            LocalDate holidayDate,
            String holidayName,
            Boolean workingDayOverride,
            String actorCcgid,
            Instant now) {
        CenterHolidayTemplateLine line = new CenterHolidayTemplateLine();
        line.id = UUID.randomUUID();
        line.templateId = templateId;
        line.holidayDate = holidayDate;
        line.holidayName = holidayName;
        line.workingDayOverride = workingDayOverride;
        line.createdAt = now;
        line.createdBy = actorCcgid;
        line.updatedAt = now;
        line.updatedBy = actorCcgid;
        return line;
    }

    public void softDelete(String actorCcgid, Instant now) {
        this.deletedAt = now;
        this.deletedBy = actorCcgid;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayName() { return holidayName; }
    public Boolean getWorkingDayOverride() { return workingDayOverride; }
    public Instant getDeletedAt() { return deletedAt; }
}
