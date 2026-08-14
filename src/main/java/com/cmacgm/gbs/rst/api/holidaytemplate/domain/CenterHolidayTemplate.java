package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Header for a Center + year legal holiday template. */
@Entity
@Table(name = "center_holiday_template")
public class CenterHolidayTemplate {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String center;

    @Column(nullable = false)
    private short year;

    @Column(name = "default_weekend_code", nullable = false, length = 40)
    private String defaultWeekendCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int version;

    @Column(name = "source_note", length = 200)
    private String sourceNote;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private String publishedBy;

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

    protected CenterHolidayTemplate() {
    }

    /**
     * Creates a draft template header.
     */
    public static CenterHolidayTemplate createDraft(
            String center,
            short year,
            String defaultWeekendCode,
            String sourceNote,
            String actorCcgid,
            Instant now) {
        CenterHolidayTemplate template = new CenterHolidayTemplate();
        template.id = UUID.randomUUID();
        template.center = center;
        template.year = year;
        template.defaultWeekendCode = defaultWeekendCode == null || defaultWeekendCode.isBlank()
                ? WeekendCode.SAT_SUN.name()
                : defaultWeekendCode;
        template.status = STATUS_DRAFT;
        template.version = 0;
        template.sourceNote = sourceNote;
        template.createdAt = now;
        template.createdBy = actorCcgid;
        template.updatedAt = now;
        template.updatedBy = actorCcgid;
        return template;
    }

    public void updateHeader(
            String defaultWeekendCode,
            String sourceNote,
            String actorCcgid,
            Instant now) {
        ensureEditable();
        if (defaultWeekendCode != null && !defaultWeekendCode.isBlank()) {
            this.defaultWeekendCode = defaultWeekendCode;
        }
        this.sourceNote = sourceNote;
        touch(actorCcgid, now);
    }

    public void markPublished(String actorCcgid, Instant now) {
        this.status = STATUS_PUBLISHED;
        this.version = this.version + 1;
        this.publishedAt = now;
        this.publishedBy = actorCcgid;
        touch(actorCcgid, now);
    }

    public void reopenDraft(String actorCcgid, Instant now) {
        this.status = STATUS_DRAFT;
        touch(actorCcgid, now);
    }

    public void softDelete(String actorCcgid, Instant now) {
        this.deletedAt = now;
        this.deletedBy = actorCcgid;
        touch(actorCcgid, now);
    }

    public void ensureEditable() {
        if (deletedAt != null) {
            throw new IllegalStateException("Template is deleted.");
        }
    }

    private void touch(String actorCcgid, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    public UUID getId() { return id; }
    public String getCenter() { return center; }
    public short getYear() { return year; }
    public String getDefaultWeekendCode() { return defaultWeekendCode; }
    public String getStatus() { return status; }
    public int getVersion() { return version; }
    public String getSourceNote() { return sourceNote; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
