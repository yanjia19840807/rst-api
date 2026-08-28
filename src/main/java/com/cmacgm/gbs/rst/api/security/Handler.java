package com.cmacgm.gbs.rst.api.security;

import java.util.Locale;
import java.util.Map;

/**
 * Who performed a user-visible action: the business subject and an optional delegate.
 */
public record Handler(
        String subjectCcgid,
        String subjectName,
        String actorCcgid,
        String actorName) {

    /**
     * Self-acted handler (no delegate).
     *
     * @param ccgid subject
     * @param name subject name
     * @return handler
     */
    public static Handler self(String ccgid, String name) {
        return new Handler(ccgid, name, null, null);
    }

    /**
     * Builds a handler from the current security principal.
     *
     * @param principal current principal
     * @return handler, or null when principal is null
     */
    public static Handler from(RstPrincipal principal) {
        if (principal == null) {
            return null;
        }
        if (principal.isDelegated()) {
            return new Handler(
                    principal.ccgid(),
                    principal.displayName(),
                    principal.actorCcgid(),
                    principal.actorDisplayName());
        }
        return self(principal.ccgid(), principal.displayName());
    }

    /**
     * @return true when a distinct delegate performed the action
     */
    public boolean hasActor() {
        return actorCcgid != null
                && !actorCcgid.isBlank()
                && !actorCcgid.equalsIgnoreCase(subjectCcgid);
    }

    /**
     * Display: {@code Name} or {@code Actor (on behalf of Subject)}.
     *
     * @return formatted name
     */
    public String displayName() {
        return displayName(Map.of());
    }

    /**
     * Display using snapshotted names, falling back to a lookup map.
     *
     * @param displayNames ccgid → name
     * @return formatted name
     */
    public String displayName(Map<String, String> displayNames) {
        String subject = firstNonBlank(subjectName, lookup(displayNames, subjectCcgid), subjectCcgid);
        if (!hasActor()) {
            return subject;
        }
        String actor = firstNonBlank(actorName, lookup(displayNames, actorCcgid), actorCcgid);
        return actor + " (on behalf of " + subject + ")";
    }

    private static String lookup(Map<String, String> displayNames, String ccgid) {
        if (displayNames == null || ccgid == null) {
            return null;
        }
        String name = displayNames.get(ccgid);
        if (name != null) {
            return name;
        }
        return displayNames.get(ccgid.toUpperCase(Locale.ROOT));
    }

    private static String firstNonBlank(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return third;
    }
}
