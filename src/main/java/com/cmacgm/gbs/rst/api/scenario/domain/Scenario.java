package com.cmacgm.gbs.rst.api.scenario.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Scenario aggregate under an Exercise (DRAFT / OFFICIAL / SUPERSEDED / DELETED). */
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

    @Column(name = "derived_from_scenario_id")
    private UUID derivedFromScenarioId;

    @Column(name = "official_at")
    private Instant officialAt;

    @Column(name = "official_by")
    private UUID officialBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScenarioAssumption> assumptions = new ArrayList<>();

    protected Scenario() {
    }

    /**
     * Creates a DRAFT scenario.
     *
     * @param exerciseId owning Exercise
     * @param scenarioCode business code unique within Exercise
     * @param name display name
     * @param description optional description
     * @param actorUserId creating Supervisor
     * @param now creation timestamp
     * @return draft scenario
     */
    public static Scenario createDraft(
            UUID exerciseId,
            String scenarioCode,
            String name,
            String description,
            UUID actorUserId,
            Instant now) {
        Scenario scenario = new Scenario();
        scenario.id = UUID.randomUUID();
        scenario.exerciseId = exerciseId;
        scenario.scenarioCode = scenarioCode;
        scenario.name = name;
        scenario.description = description;
        scenario.status = "DRAFT";
        scenario.createdAt = now;
        scenario.createdBy = actorUserId;
        scenario.updatedAt = now;
        scenario.updatedBy = actorUserId;
        return scenario;
    }

    /**
     * Supersedes an Official scenario and clones a new DRAFT revision for Supervisor rework.
     *
     * <p>Copies name/description with a {@code -R1} style code suffix and sets lineage via
     * {@code derived_from_scenario_id}. Callers must copy assumptions onto the returned draft.
     *
     * @param official Official scenario being superseded
     * @param revisionCode unique draft code within the Exercise (e.g. {@code SCN-1-R1})
     * @param actorUserId actor
     * @param now timestamp
     * @return new DRAFT scenario derived from the official
     */
    public static Scenario supersedeOfficialAndCloneDraft(
            Scenario official, String revisionCode, UUID actorUserId, Instant now) {
        official.markSuperseded(actorUserId, now);
        Scenario draft = createDraft(
                official.exerciseId,
                revisionCode,
                official.name,
                official.description,
                actorUserId,
                now);
        draft.derivedFromScenarioId = official.id;
        return draft;
    }

    /**
     * Updates draft scenario header fields.
     *
     * @param name display name
     * @param description optional description
     * @param actorUserId updating Supervisor
     * @param now update timestamp
     */
    public void updateDraft(String name, String description, UUID actorUserId, Instant now) {
        this.name = name;
        this.description = description;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Replaces all assumptions for a DRAFT scenario.
     *
     * <p>Upserts by {@code parameter_code} so Hibernate does not INSERT a duplicate row before
     * deleting the old one (which violates {@code uk_scenario_assumption}).
     *
     * @param replacements new assumptions
     * @param actorUserId updating Supervisor
     * @param now update timestamp
     */
    public void replaceAssumptions(List<ScenarioAssumption> replacements, UUID actorUserId, Instant now) {
        Map<String, ScenarioAssumption> incoming = new LinkedHashMap<>();
        for (ScenarioAssumption assumption : replacements) {
            incoming.put(assumption.getParameterCode(), assumption);
        }

        assumptions.removeIf(existing -> !incoming.containsKey(existing.getParameterCode()));

        Map<String, ScenarioAssumption> existingByCode = new LinkedHashMap<>();
        for (ScenarioAssumption existing : assumptions) {
            existingByCode.put(existing.getParameterCode(), existing);
        }
        for (ScenarioAssumption next : incoming.values()) {
            ScenarioAssumption current = existingByCode.get(next.getParameterCode());
            if (current != null) {
                current.overwriteValues(next, actorUserId, now);
            } else {
                next.attach(this);
                assumptions.add(next);
            }
        }
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Marks this scenario OFFICIAL.
     *
     * @param actorUserId Supervisor making it official
     * @param now official timestamp
     */
    public void markOfficial(UUID actorUserId, Instant now) {
        this.status = "OFFICIAL";
        this.officialAt = now;
        this.officialBy = actorUserId;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Supersedes a previous official scenario.
     *
     * @param actorUserId actor
     * @param now timestamp
     */
    public void markSuperseded(UUID actorUserId, Instant now) {
        this.status = "SUPERSEDED";
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    /**
     * Soft-deletes a draft scenario.
     *
     * @param actorUserId deleting Supervisor
     * @param now deletion timestamp
     */
    public void softDelete(UUID actorUserId, Instant now) {
        this.status = "DELETED";
        this.deletedAt = now;
        this.deletedBy = actorUserId;
        this.updatedAt = now;
        this.updatedBy = actorUserId;
    }

    public UUID getId() { return id; }
    public UUID getExerciseId() { return exerciseId; }
    public String getScenarioCode() { return scenarioCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Instant getOfficialAt() { return officialAt; }
    public UUID getOfficialBy() { return officialBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public long getVersion() { return version; }
    public List<ScenarioAssumption> getAssumptions() { return Collections.unmodifiableList(assumptions); }
}
