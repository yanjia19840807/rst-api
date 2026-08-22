package com.cmacgm.gbs.rst.api.toolkit.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "toolkit")
public class Toolkit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(name = "supervisor_position_id", nullable = false, length = 80)
    private String supervisorPositionId;

    @Column(nullable = false, length = 120)
    private String center;

    @Column(nullable = false, length = 120)
    private String domain;

    @Column(nullable = false, length = 200)
    private String pl1;

    @Column(nullable = false, length = 200)
    private String pl2;

    @Column(name = "pl3_name", nullable = false, length = 200)
    private String pl3Name;

    @Column(name = "primary_pl3_code", nullable = false, length = 80)
    private String primaryPl3Code;

    @Column(name = "owner_ccgid", length = 64)
    private String ownerCcgid;

    /**
     * When true, SYSTEM Cycle Time is the sum of each subtask's median seconds per unit.
     * When false, it is the median of all included sessions.
     */
    @Column(name = "combine_subtasks_time", nullable = false)
    private boolean combineSubtasksTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 64)
    private String deletedBy;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "toolkit", cascade = CascadeType.ALL)
    @OrderBy("displayOrder ASC")
    private List<ToolkitSubtask> subtasks = new ArrayList<>();

    @OneToMany(mappedBy = "toolkit", cascade = CascadeType.ALL)
    @Fetch(FetchMode.SUBSELECT)
    private List<ToolkitSharedKpiSelection> sharedKpiSelections = new ArrayList<>();

    protected Toolkit() {
    }

    public static Toolkit create(
            String name,
            String description,
            String supervisorPositionId,
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            boolean combineSubtasksTime,
            String ownerCcgid,
            Instant now) {
        Toolkit toolkit = new Toolkit();
        toolkit.name = name.trim();
        toolkit.description = normalize(description);
        toolkit.supervisorPositionId = supervisorPositionId;
        toolkit.center = center;
        toolkit.domain = domain;
        toolkit.pl1 = pl1;
        toolkit.pl2 = pl2;
        toolkit.primaryPl3Code = pl3Code;
        toolkit.pl3Name = pl3Name;
        toolkit.combineSubtasksTime = combineSubtasksTime;
        toolkit.ownerCcgid = ownerCcgid;
        toolkit.createdAt = now;
        toolkit.createdBy = ownerCcgid;
        toolkit.updatedAt = now;
        toolkit.updatedBy = ownerCcgid;
        return toolkit;
    }

    public void update(
            String name,
            String description,
            boolean combineSubtasksTime,
            String ownerCcgid,
            Instant now) {
        this.name = name.trim();
        this.description = normalize(description);
        this.combineSubtasksTime = combineSubtasksTime;
        this.ownerCcgid = ownerCcgid;
        this.updatedAt = now;
        this.updatedBy = ownerCcgid;
    }

    public ToolkitSubtask addSubtask(String name, String description, int displayOrder, Instant now) {
        ToolkitSubtask subtask =
                ToolkitSubtask.create(this, name, description, displayOrder, ownerCcgid, now);
        subtasks.add(subtask);
        return subtask;
    }

    public ToolkitSharedKpiSelection selectKpi(
            String carrier, String site, String country, Instant now) {
        ToolkitSharedKpiSelection selection =
                ToolkitSharedKpiSelection.create(this, carrier, site, country, ownerCcgid, now);
        sharedKpiSelections.add(selection);
        return selection;
    }

    public void softDelete(Instant now) {
        deletedAt = now;
        deletedBy = ownerCcgid;
        updatedAt = now;
        updatedBy = ownerCcgid;
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

    public String getSupervisorPositionId() {
        return supervisorPositionId;
    }

    public String getCenter() {
        return center;
    }

    public String getDomain() {
        return domain;
    }

    public String getPl1() {
        return pl1;
    }

    public String getPl2() {
        return pl2;
    }

    public String getPl3Name() {
        return pl3Name;
    }

    public String getPrimaryPl3Code() {
        return primaryPl3Code;
    }

    public boolean isCombineSubtasksTime() {
        return combineSubtasksTime;
    }

    public long getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getOwnerCcgid() {
        return ownerCcgid;
    }

    String ownerForAudit() {
        return ownerCcgid;
    }

    public List<ToolkitSubtask> getSubtasks() {
        return subtasks.stream().filter(item -> item.getDeletedAt() == null).toList();
    }

    public List<ToolkitSubtask> getAllSubtasks() {
        return Collections.unmodifiableList(subtasks);
    }

    public List<ToolkitSharedKpiSelection> getSharedKpiSelections() {
        return Collections.unmodifiableList(sharedKpiSelections);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
