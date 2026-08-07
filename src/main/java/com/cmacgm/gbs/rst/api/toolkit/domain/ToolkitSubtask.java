package com.cmacgm.gbs.rst.api.toolkit.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.cmacgm.gbs.rst.api.identity.domain.AppUser;

@Entity
@Table(name = "toolkit_subtask")
public class ToolkitSubtask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "toolkit_id", nullable = false)
    private Toolkit toolkit;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private AppUser deletedBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected ToolkitSubtask() {
    }

    static ToolkitSubtask create(
            Toolkit toolkit,
            String name,
            String description,
            int displayOrder,
            AppUser actor,
            Instant now) {
        ToolkitSubtask subtask = new ToolkitSubtask();
        subtask.toolkit = toolkit;
        subtask.name = name.trim();
        subtask.description = description == null || description.isBlank() ? null : description.trim();
        subtask.displayOrder = displayOrder;
        subtask.createdAt = now;
        subtask.createdBy = actor;
        subtask.updatedAt = now;
        subtask.updatedBy = actor;
        return subtask;
    }

    public void softDelete(Instant now) {
        deletedAt = now;
        deletedBy = toolkit.ownerForAudit();
        updatedAt = now;
        updatedBy = toolkit.ownerForAudit();
    }

    public void update(String name, String description, int displayOrder, boolean deleted, Instant now) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.displayOrder = displayOrder;
        this.deletedAt = deleted ? (deletedAt == null ? now : deletedAt) : null;
        this.deletedBy = deleted ? toolkit.ownerForAudit() : null;
        this.updatedAt = now;
        this.updatedBy = toolkit.ownerForAudit();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
