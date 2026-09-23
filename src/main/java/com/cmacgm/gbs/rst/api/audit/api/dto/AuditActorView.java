package com.cmacgm.gbs.rst.api.audit.api.dto;

import com.cmacgm.gbs.rst.api.audit.domain.AuditEvent;
import com.cmacgm.gbs.rst.api.security.Handler;

/**
 * Who created or last changed a business row, including a delegate when one acted.
 */
public record AuditActorView(
        String actorCcgid,
        String actorName,
        String subjectCcgid,
        String subjectName,
        String subjectPositionId,
        boolean delegated) {

    /**
     * @param event stored audit row
     * @return view, or null when event is null
     */
    public static AuditActorView from(AuditEvent event) {
        if (event == null) {
            return null;
        }
        String actorCcgid = event.getActorCcgid();
        String subjectCcgid = event.getSubjectCcgid();
        boolean samePerson = actorCcgid != null && actorCcgid.equalsIgnoreCase(subjectCcgid);
        boolean delegated = !samePerson || event.getSubjectPositionId() != null;
        return new AuditActorView(
                actorCcgid,
                first(event.getActorName(), actorCcgid),
                subjectCcgid,
                first(event.getSubjectName(), subjectCcgid),
                event.getSubjectPositionId(),
                delegated);
    }

    /**
     * Handler snapshot: subject first, delegate as actor when distinct.
     *
     * @param handler subject plus optional delegate
     * @return view, or null when handler is null
     */
    public static AuditActorView from(Handler handler) {
        return from(handler, null);
    }

    /**
     * Handler snapshot with the covered position.
     *
     * @param handler subject plus optional delegate
     * @param positionId covered Timesheet position
     * @return view, or null when handler is null
     */
    public static AuditActorView from(Handler handler, String positionId) {
        if (handler == null) {
            return null;
        }
        boolean delegated = handler.hasActor();
        String actorCcgid = delegated ? handler.actorCcgid() : handler.subjectCcgid();
        String actorName = delegated ? handler.actorName() : handler.subjectName();
        return new AuditActorView(
                actorCcgid,
                first(actorName, actorCcgid),
                handler.subjectCcgid(),
                first(handler.subjectName(), handler.subjectCcgid()),
                positionId,
                delegated);
    }

    /**
     * Self-acted occupant (no delegate).
     *
     * @param ccgid subject
     * @param name subject name
     * @return view, or null when both are blank
     */
    public static AuditActorView self(String ccgid, String name) {
        if ((ccgid == null || ccgid.isBlank()) && (name == null || name.isBlank())) {
            return null;
        }
        return new AuditActorView(ccgid, first(name, ccgid), ccgid, first(name, ccgid), null, false);
    }

    /**
     * Occupant via a distinct delegate.
     *
     * @param actorCcgid delegate
     * @param actorName delegate name
     * @param subjectCcgid occupant
     * @param subjectName occupant name
     * @param positionId covered position
     * @return view
     */
    public static AuditActorView via(
            String actorCcgid,
            String actorName,
            String subjectCcgid,
            String subjectName,
            String positionId) {
        boolean delegated = actorCcgid != null
                && !actorCcgid.isBlank()
                && (subjectCcgid == null || !actorCcgid.equalsIgnoreCase(subjectCcgid));
        return new AuditActorView(
                actorCcgid,
                first(actorName, actorCcgid),
                subjectCcgid,
                first(subjectName, subjectCcgid),
                positionId,
                delegated);
    }

    /**
     * One-line label: {@code Name} or {@code Subject via Actor}.
     *
     * @return formatted name, or null when empty
     */
    public String displayName() {
        String subject = first(subjectName, subjectCcgid);
        boolean via = delegated
                && actorCcgid != null
                && !actorCcgid.isBlank()
                && (subjectCcgid == null || !actorCcgid.equalsIgnoreCase(subjectCcgid));
        if (!via) {
            return subject;
        }
        return subject + " via " + first(actorName, actorCcgid);
    }

    private static String first(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
