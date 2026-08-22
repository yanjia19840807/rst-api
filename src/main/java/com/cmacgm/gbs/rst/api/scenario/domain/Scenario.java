package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Scenario aggregate under an Exercise (DRAFT / DELETED). Official is an Exercise pointer. */
@Entity
@Table(name = "scenario")
public class Scenario {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "scenario_code", nullable = false, length = 40)
    private String scenarioCode;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "right_sizing_hc", precision = 18, scale = 6)
    private BigDecimal rightSizingHc;

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
    private long version;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("shiftNo ASC")
    private List<ScenarioShift> shifts = new ArrayList<>();

    protected Scenario() {
    }

    /**
     * Creates a DRAFT scenario.
     *
     * @param exerciseId owning Exercise
     * @param scenarioCode business code unique within Exercise
     * @param name display name
     * @param description optional description
     * @param rightSizingHc Right Sizing HC; null stores 0
     * @param actorCcgid creating Supervisor
     * @param now creation timestamp
     * @return draft scenario
     */
    public static Scenario createDraft(
            UUID exerciseId,
            String scenarioCode,
            String name,
            String description,
            BigDecimal rightSizingHc,
            String actorCcgid,
            Instant now) {
        Scenario scenario = new Scenario();
        scenario.id = UUID.randomUUID();
        scenario.exerciseId = exerciseId;
        scenario.scenarioCode = scenarioCode;
        scenario.name = name;
        scenario.description = description;
        scenario.status = "DRAFT";
        scenario.rightSizingHc = rightSizingHc != null ? rightSizingHc : BigDecimal.ZERO;
        scenario.createdAt = now;
        scenario.createdBy = actorCcgid;
        scenario.updatedAt = now;
        scenario.updatedBy = actorCcgid;
        return scenario;
    }

    /**
     * Returns whether the scenario can still be edited on an In Progress exercise.
     * Official is an Exercise pointer, not a row status.
     */
    public boolean isWorking() {
        return "DRAFT".equals(status);
    }

    /**
     * Updates working-scenario header fields.
     *
     * @param name display name
     * @param description optional description
     * @param rightSizingHc Right Sizing HC; null keeps the stored value
     * @param actorCcgid updating Supervisor
     * @param now update timestamp
     */
    public void updateDraft(
            String name,
            String description,
            BigDecimal rightSizingHc,
            String actorCcgid,
            Instant now) {
        this.name = name;
        this.description = description;
        if (rightSizingHc != null) {
            this.rightSizingHc = rightSizingHc;
        }
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Replaces Slot Simulation shift inputs for a DRAFT scenario.
     *
     * <p>Upserts by {@code shift_no} so Hibernate does not INSERT a duplicate before
     * deleting the old row (which violates {@code uk_scenario_shift}).
     */
    public void replaceShifts(List<ScenarioShift> replacements, String actorCcgid, Instant now) {
        Map<Short, ScenarioShift> incoming = new LinkedHashMap<>();
        for (ScenarioShift shift : replacements) {
            incoming.put(shift.getShiftNo(), shift);
        }

        shifts.removeIf(existing -> !incoming.containsKey(existing.getShiftNo()));

        Map<Short, ScenarioShift> existingByNo = new LinkedHashMap<>();
        for (ScenarioShift existing : shifts) {
            existingByNo.put(existing.getShiftNo(), existing);
        }
        for (ScenarioShift next : incoming.values()) {
            ScenarioShift current = existingByNo.get(next.getShiftNo());
            if (current != null) {
                current.overwriteValues(next, actorCcgid, now);
            } else {
                next.attach(this);
                shifts.add(next);
            }
        }
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    /**
     * Soft-deletes a draft scenario.
     *
     * @param actorCcgid deleting Supervisor
     * @param now deletion timestamp
     */
    public void softDelete(String actorCcgid, Instant now) {
        this.status = "DELETED";
        this.deletedAt = now;
        this.deletedBy = actorCcgid;
        this.updatedAt = now;
        this.updatedBy = actorCcgid;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public String getScenarioCode() { return scenarioCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public BigDecimal getRightSizingHc() { return rightSizingHc; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }
    public List<ScenarioShift> getShifts() { return Collections.unmodifiableList(shifts); }
}
