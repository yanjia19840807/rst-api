package com.cmacgm.gbs.rst.api.audit.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;
import com.cmacgm.gbs.rst.api.audit.domain.AuditAction;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEvent;
import com.cmacgm.gbs.rst.api.audit.persistence.AuditEventRepository;
import com.cmacgm.gbs.rst.api.delegation.application.PositionCoverage;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one audit event for a business change in the current transaction.
 */
@Service
public class AuditRecorder {

    private final AuditEventRepository events;
    private final PositionCoverage coverage;
    private final TimesheetReadService timesheet;
    private final Clock clock;

    /**
     * @param events store
     * @param coverage covered position and subject person
     * @param timesheet display names
     * @param clock time
     */
    public AuditRecorder(
            AuditEventRepository events,
            PositionCoverage coverage,
            TimesheetReadService timesheet,
            Clock clock) {
        this.events = events;
        this.coverage = coverage;
        this.timesheet = timesheet;
        this.clock = clock;
    }

    /**
     * @param entityType aggregate
     * @param entityId business id
     * @param action what changed
     * @param roleType seat role used to resolve the subject person
     * @return saved event
     */
    @Transactional
    public AuditEvent record(AuditEntityType entityType, UUID entityId, AuditAction action, String roleType) {
        RstPrincipal principal = currentPrincipal();
        String actorCcgid = principal == null ? "SYSTEM" : principal.realCcgid();
        String actorName = principal == null ? "SYSTEM" : principal.actorDisplayName();
        String positionId = coverage.positionId(roleType);
        String subjectCcgid = principal == null
                ? actorCcgid
                : coverage.subjectCcgid(principal.ccgid(), roleType);
        String subjectName = displayName(subjectCcgid);
        AuditEvent event = AuditEvent.record(
                entityType,
                entityId,
                action,
                subjectCcgid,
                subjectName,
                actorCcgid,
                displayName(actorCcgid, actorName),
                positionId,
                clock.instant());
        return events.save(event);
    }

    /**
     * One event for a Timesheet sync run. A scheduled run has no login, so both
     * people are {@code SYSTEM}. A delegate keeps the signed-in user as the actor
     * and the covered position on the event.
     *
     * @param runId sync run id
     * @return saved event
     */
    /**
     * One event for the signed-in user. A delegate stays the actor; the covered
     * position and its occupant are stored on the event. No login means {@code SYSTEM}.
     *
     * @param entityType aggregate
     * @param entityId business id
     * @param action what changed
     * @return saved event
     */
    @Transactional
    public AuditEvent recordCurrent(AuditEntityType entityType, UUID entityId, AuditAction action) {
        RstPrincipal principal = currentPrincipal();
        String actorCcgid = principal == null ? "SYSTEM" : principal.realCcgid();
        String actorName = principal == null ? "SYSTEM" : principal.actorDisplayName();
        String positionId = principal == null ? null : principal.delegatedPositionId();
        String subjectCcgid = actorCcgid;
        if (principal != null && positionId != null) {
            var occupant = timesheet.occupant(positionId);
            if (occupant != null
                    && occupant.ccgid() != null
                    && !occupant.ccgid().isBlank()
                    && !occupant.ccgid().equalsIgnoreCase(positionId)) {
                subjectCcgid = occupant.ccgid();
            }
        } else if (principal != null && principal.isDelegated()) {
            subjectCcgid = principal.ccgid();
        }
        AuditEvent event = AuditEvent.record(
                entityType,
                entityId,
                action,
                subjectCcgid,
                displayName(subjectCcgid),
                actorCcgid,
                displayName(actorCcgid, actorName),
                positionId,
                clock.instant());
        return events.save(event);
    }

    /**
     * One event for a Timesheet sync run.
     *
     * @param runId sync run id
     * @return saved event
     */
    @Transactional
    public AuditEvent recordSync(UUID runId) {
        return recordCurrent(AuditEntityType.TIMESHEET_SYNC, runId, AuditAction.CREATE);
    }

    /**
     * @param id latest event id
     * @return display label, or null
     */
    /**
     * @param id latest event id
     * @return when that event happened, or null
     */
    @Transactional(readOnly = true)
    public Instant occurredAt(UUID id) {
        if (id == null) {
            return null;
        }
        return events.findById(id).map(AuditEvent::getOccurredAt).orElse(null);
    }

    /**
     * @param id latest event id
     * @return display label, or null
     */
    @Transactional(readOnly = true)
    public String label(UUID id) {
        if (id == null) {
            return null;
        }
        return events.findById(id).map(AuditEvent::processedByLabel).orElse(null);
    }

    /**
     * @param entityType aggregate
     * @param entityId business id
     * @return creator from the first CREATE event, or null
     */
    @Transactional(readOnly = true)
    public AuditActorView createdBy(AuditEntityType entityType, UUID entityId) {
        if (entityId == null) {
            return null;
        }
        return createdBy(entityType, List.of(entityId)).get(entityId);
    }

    /**
     * @param entityType aggregate
     * @param entityIds business ids
     * @return first CREATE actor per id
     */
    @Transactional(readOnly = true)
    public Map<UUID, AuditActorView> createdBy(AuditEntityType entityType, Collection<UUID> entityIds) {
        if (entityType == null || entityIds == null || entityIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = entityIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AuditEvent> earliest = new HashMap<>();
        for (AuditEvent event : events.findByEntityTypeAndActionAndEntityIdIn(
                entityType, AuditAction.CREATE, ids)) {
            AuditEvent current = earliest.get(event.getEntityId());
            if (current == null || event.getOccurredAt().isBefore(current.getOccurredAt())) {
                earliest.put(event.getEntityId(), event);
            }
        }
        Map<UUID, AuditActorView> result = new HashMap<>();
        earliest.forEach((id, event) -> result.put(id, named(AuditActorView.from(event))));
        return result;
    }

    /**
     * @param eventId stored event id
     * @return actor on that event, or null
     */
    @Transactional(readOnly = true)
    public AuditActorView actor(UUID eventId) {
        if (eventId == null) {
            return null;
        }
        return events.findById(eventId).map(event -> named(AuditActorView.from(event))).orElse(null);
    }

    /**
     * @param eventIds stored event ids
     * @return actor per event id
     */
    @Transactional(readOnly = true)
    public Map<UUID, AuditActorView> actors(Collection<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = eventIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AuditActorView> result = new HashMap<>();
        for (AuditEvent event : events.findByIdIn(ids)) {
            result.put(event.getId(), named(AuditActorView.from(event)));
        }
        return result;
    }

    private AuditActorView named(AuditActorView view) {
        if (view == null) {
            return null;
        }
        return new AuditActorView(
                view.actorCcgid(),
                displayName(view.actorCcgid(), view.actorName()),
                view.subjectCcgid(),
                displayName(view.subjectCcgid(), view.subjectName()),
                view.subjectPositionId(),
                view.delegated());
    }

    private String displayName(String ccgid) {
        return displayName(ccgid, null);
    }

    private String displayName(String ccgid, String fallback) {
        if (ccgid != null && !ccgid.isBlank()) {
            String live = timesheet.findDisplayName(ccgid).orElse(null);
            if (live != null && !live.isBlank()) {
                return live;
            }
        }
        if (fallback != null && !fallback.isBlank() && (ccgid == null || !fallback.equalsIgnoreCase(ccgid))) {
            return fallback;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return ccgid;
    }

    private static RstPrincipal currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof RstPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
