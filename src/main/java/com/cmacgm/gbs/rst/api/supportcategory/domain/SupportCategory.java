package com.cmacgm.gbs.rst.api.supportcategory.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Global Production Support standard category, maintained in the database. */
@Entity
@Table(name = "support_category")
public class SupportCategory {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

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

    protected SupportCategory() {
    }

    /**
     * Creates an active category.
     */
    public static SupportCategory create(
            String name, int displayOrder, String actorCcgid, Instant now) {
        SupportCategory category = new SupportCategory();
        category.id = UUID.randomUUID();
        category.name = name;
        category.status = STATUS_ACTIVE;
        category.displayOrder = displayOrder;
        category.createdAt = now;
        category.createdBy = actorCcgid;
        category.updatedAt = now;
        category.updatedBy = actorCcgid;
        return category;
    }

    /**
     * Renames this category.
     */
    public void rename(String name, String actorCcgid, Instant now) {
        ensureEditable();
        this.name = name;
        touch(actorCcgid, now);
    }

    /**
     * Sets ACTIVE or INACTIVE.
     */
    public void setStatus(String status, String actorCcgid, Instant now) {
        ensureEditable();
        this.status = status;
        touch(actorCcgid, now);
    }

    /**
     * Soft-deletes this category.
     */
    public void softDelete(String actorCcgid, Instant now) {
        ensureEditable();
        this.deletedAt = now;
        this.deletedBy = actorCcgid;
        touch(actorCcgid, now);
    }

    /**
     * Rejects edits on a deleted row.
     */
    public void ensureEditable() {
        if (deletedAt != null) {
            throw new IllegalStateException("Category is deleted.");
        }
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    private void touch(String actorCcgid, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
