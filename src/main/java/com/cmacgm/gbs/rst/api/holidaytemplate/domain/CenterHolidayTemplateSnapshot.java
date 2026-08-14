package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable published snapshot of a Center holiday template. */
@Entity
@Table(name = "center_holiday_template_snapshot")
public class CenterHolidayTemplateSnapshot {

    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 120)
    private String center;

    @Column(nullable = false)
    private short year;

    @Column(name = "default_weekend_code", nullable = false, length = 40)
    private String defaultWeekendCode;

    @Column(name = "source_note", length = 200)
    private String sourceNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lines_json", nullable = false, columnDefinition = "jsonb")
    private String linesJson;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by")
    private String publishedBy;

    protected CenterHolidayTemplateSnapshot() {
    }

    public static CenterHolidayTemplateSnapshot create(
            CenterHolidayTemplate template,
            String linesJson,
            String actorCcgid,
            Instant now) {
        CenterHolidayTemplateSnapshot snapshot = new CenterHolidayTemplateSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.templateId = template.getId();
        snapshot.version = template.getVersion();
        snapshot.center = template.getCenter();
        snapshot.year = template.getYear();
        snapshot.defaultWeekendCode = template.getDefaultWeekendCode();
        snapshot.sourceNote = template.getSourceNote();
        snapshot.linesJson = linesJson;
        snapshot.publishedAt = now;
        snapshot.publishedBy = actorCcgid;
        return snapshot;
    }

    public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public int getVersion() { return version; }
    public String getCenter() { return center; }
    public short getYear() { return year; }
    public String getDefaultWeekendCode() { return defaultWeekendCode; }
    public String getSourceNote() { return sourceNote; }
    public String getLinesJson() { return linesJson; }
    public Instant getPublishedAt() { return publishedAt; }
}
