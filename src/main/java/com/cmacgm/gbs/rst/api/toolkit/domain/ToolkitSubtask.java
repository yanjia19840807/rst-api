package com.cmacgm.gbs.rst.api.toolkit.domain;

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

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Version
    @Column(nullable = false)
    private long version;

    protected ToolkitSubtask() {
    }

    static ToolkitSubtask create(
            Toolkit toolkit, String name, String description, int displayOrder) {
        ToolkitSubtask subtask = new ToolkitSubtask();
        subtask.toolkit = toolkit;
        subtask.name = name.trim();
        subtask.description = description == null || description.isBlank() ? null : description.trim();
        subtask.displayOrder = displayOrder;
        subtask.enabled = true;
        return subtask;
    }

    public void softDelete() {
        this.deleted = true;
    }

    public void rename(String name, String description, int displayOrder) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.displayOrder = displayOrder;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void update(String name, String description, int displayOrder, boolean deleted) {
        rename(name, description, displayOrder);
        this.deleted = deleted;
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

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
