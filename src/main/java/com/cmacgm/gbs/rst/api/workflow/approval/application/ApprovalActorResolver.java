package com.cmacgm.gbs.rst.api.workflow.approval.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;
import com.cmacgm.gbs.rst.api.delegation.domain.Delegation;
import com.cmacgm.gbs.rst.api.delegation.persistence.DelegationRepository;
import com.cmacgm.gbs.rst.api.security.Handler;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.Occupant;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskActor;
import org.springframework.stereotype.Component;

/**
 * Resolves approval actors for recording and display: occupant first, via a
 * distinct delegate when one is covering the position.
 */
@Component
public class ApprovalActorResolver {

    private final TimesheetReadService timesheet;
    private final DelegationRepository delegations;
    private final Clock clock;

    /**
     * Creates the resolver.
     *
     * @param timesheet live occupant and names
     * @param delegations open position grants
     * @param clock now for usable grants
     */
    public ApprovalActorResolver(
            TimesheetReadService timesheet,
            DelegationRepository delegations,
            Clock clock) {
        this.timesheet = timesheet;
        this.delegations = delegations;
        this.clock = clock;
    }

    /**
     * Handler for the current principal. Position coverage uses the occupant as
     * subject and the signed-in delegate as actor.
     *
     * @param principal current caller
     * @return handler, or null when principal is null
     */
    public Handler handlerFor(RstPrincipal principal) {
        if (principal == null) {
            return null;
        }
        String positionId = principal.delegatedPositionId();
        if (hasText(positionId)) {
            Occupant occupant = timesheet.occupant(positionId);
            if (occupant != null
                    && hasText(occupant.ccgid())
                    && !occupant.ccgid().equalsIgnoreCase(positionId)) {
                return new Handler(
                        occupant.ccgid(),
                        firstNonBlank(occupant.name(), nameOf(occupant.ccgid(), Map.of())),
                        principal.realCcgid(),
                        firstNonBlank(principal.actorDisplayName(), nameOf(principal.realCcgid(), Map.of())));
            }
        }
        return Handler.from(principal);
    }

    /**
     * Live occupant of a position, via an open usable grant when one exists.
     *
     * @param positionId Timesheet position
     * @param fallbackCcgid stored occupant when Timesheet has none
     * @param names ccgid → name fallback
     * @return actor view, or null when nobody is known
     */
    public AuditActorView forPosition(String positionId, String fallbackCcgid, Map<String, String> names) {
        Occupant occupant = hasText(positionId) ? timesheet.occupant(positionId) : null;
        String subjectCcgid = occupant != null && hasText(occupant.ccgid())
                ? occupant.ccgid()
                : fallbackCcgid;
        String subjectName = occupant != null && hasText(occupant.name())
                ? occupant.name()
                : nameOf(subjectCcgid, names);
        Delegation grant = latestUsable(positionId);
        if (grant != null
                && hasText(grant.getDelegateCcgid())
                && (subjectCcgid == null || !grant.getDelegateCcgid().equalsIgnoreCase(subjectCcgid))) {
            return AuditActorView.via(
                    grant.getDelegateCcgid(),
                    firstNonBlank(grant.getDelegateName(), nameOf(grant.getDelegateCcgid(), names)),
                    subjectCcgid,
                    subjectName,
                    positionId);
        }
        return AuditActorView.self(subjectCcgid, subjectName);
    }

    /**
     * Stored handler on a completed (or pending) task actor, with live names.
     *
     * @param actor task actor
     * @param names ccgid → name fallback
     * @return actor view, or null when actor is null
     */
    public AuditActorView fromActor(TaskActor actor, Map<String, String> names) {
        if (actor == null) {
            return null;
        }
        Handler handler = actor.handler();
        String subject = nameOf(handler.subjectCcgid(), names, handler.subjectName());
        if (!handler.hasActor()) {
            return AuditActorView.self(handler.subjectCcgid(), subject);
        }
        return AuditActorView.via(
                handler.actorCcgid(),
                nameOf(handler.actorCcgid(), names, handler.actorName()),
                handler.subjectCcgid(),
                subject,
                actor.getPositionId());
    }

    private Delegation latestUsable(String positionId) {
        if (!hasText(positionId)) {
            return null;
        }
        Instant now = clock.instant();
        return delegations.findBySubjectPositionIdIn(List.of(positionId)).stream()
                .filter(row -> row.isUsable(now))
                .max(Comparator.comparing(Delegation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private String nameOf(String ccgid, Map<String, String> names, String stored) {
        if (hasText(stored) && (ccgid == null || !stored.equalsIgnoreCase(ccgid))) {
            return stored;
        }
        return nameOf(ccgid, names);
    }

    private String nameOf(String ccgid, Map<String, String> names) {
        if (!hasText(ccgid)) {
            return null;
        }
        if (names != null) {
            String mapped = names.get(ccgid);
            if (hasText(mapped) && !mapped.equalsIgnoreCase(ccgid)) {
                return mapped;
            }
        }
        return timesheet.displayNameByCcgid(ccgid);
    }

    private static String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        return second;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
