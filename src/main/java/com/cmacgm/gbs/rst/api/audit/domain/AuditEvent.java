package com.cmacgm.gbs.rst.api.audit.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One create, update, delete, enable, disable, or submit.
 * The row is append-only.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "subject_ccgid", nullable = false, length = 64)
    private String subjectCcgid;

    @Column(name = "subject_name", length = 200)
    private String subjectName;

    @Column(name = "actor_ccgid", nullable = false, length = 64)
    private String actorCcgid;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Column(name = "subject_position_id", length = 80)
    private String subjectPositionId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    /**
     * @param entityType aggregate
     * @param entityId business id
     * @param action what changed
     * @param subjectCcgid person the data belongs to
     * @param subjectName subject display name
     * @param actorCcgid person who performed the action
     * @param actorName actor display name
     * @param subjectPositionId covered position, or null
     * @param occurredAt when
     * @return new event
     */
    public static AuditEvent record(
            AuditEntityType entityType,
            UUID entityId,
            AuditAction action,
            String subjectCcgid,
            String subjectName,
            String actorCcgid,
            String actorName,
            String subjectPositionId,
            Instant occurredAt) {
        AuditEvent event = new AuditEvent();
        event.id = UUID.randomUUID();
        event.entityType = entityType;
        event.entityId = entityId;
        event.action = action;
        event.subjectCcgid = subjectCcgid;
        event.subjectName = subjectName;
        event.actorCcgid = actorCcgid;
        event.actorName = actorName;
        event.subjectPositionId = blankToNull(subjectPositionId);
        event.occurredAt = occurredAt;
        return event;
    }

    /**
     * @return name only, or {@code Actor (delegate for Subject)} / position
     */
    public String processedByLabel() {
        String actor = first(actorName, actorCcgid);
        boolean samePerson = actorCcgid != null && actorCcgid.equalsIgnoreCase(subjectCcgid);
        if (samePerson && subjectPositionId == null) {
            return actor;
        }
        String target = samePerson ? subjectPositionId : first(subjectName, subjectCcgid);
        return actor + " (delegate for " + target + ")";
    }

    public UUID getId() {
        return id;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getSubjectCcgid() {
        return subjectCcgid;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getActorCcgid() {
        return actorCcgid;
    }

    public String getActorName() {
        return actorName;
    }

    public String getSubjectPositionId() {
        return subjectPositionId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    private static String first(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
