package com.cmacgm.gbs.rst.api.mail.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Mail types a signed-in Supervisor / Manager / CDH / LTH / ADMIN can opt into.
 */
public enum MailType {
    WORKFLOW("workflow.notification", "Workflow notifications"),
    TIMESHEET_SYNC_FAILED("timesheet.sync.failed", "Timesheet sync failed");

    private static final Set<String> WORKFLOW_ALIASES = Set.of(
            "workflow.notification",
            "WORKFLOW",
            "approval.requested",
            "APPROVAL_REQUESTED",
            "submission.outcome",
            "SUBMISSION_OUTCOME",
            "submission.returned",
            "submission.approved",
            "SUBMISSION_RETURNED",
            "SUBMISSION_APPROVED");

    private static final List<String> LEGACY_WORKFLOW_IDS = List.of(
            "approval.requested",
            "submission.outcome");

    private final String id;
    private final String label;

    MailType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /**
     * Stored preference ids that used to be separate workflow switches.
     *
     * @return legacy ids
     */
    public static List<String> legacyWorkflowIds() {
        return LEGACY_WORKFLOW_IDS;
    }

    /**
     * @param raw slug or enum name
     * @return type, or null
     */
    public static MailType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (WORKFLOW_ALIASES.contains(trimmed) || WORKFLOW_ALIASES.contains(trimmed.toUpperCase(Locale.ROOT))) {
            return WORKFLOW;
        }
        for (MailType type : values()) {
            if (type.id.equals(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Types this product role may receive. One person has one role.
     *
     * @param role SUPERVISOR / MANAGER / CDH / LTH / ADMIN
     * @return types, possibly empty
     */
    public static List<MailType> forRole(String role) {
        if (role == null) {
            return List.of();
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "SUPERVISOR", "MANAGER", "CDH" -> List.of(WORKFLOW);
            case "LTH" -> List.of(WORKFLOW, TIMESHEET_SYNC_FAILED);
            case "ADMIN" -> List.of(TIMESHEET_SYNC_FAILED);
            default -> List.of();
        };
    }

    /**
     * @param roles token roles
     * @return the single mail-capable role, or null
     */
    public static String mailRole(Iterable<String> roles) {
        if (roles == null) {
            return null;
        }
        for (String candidate : List.of("SUPERVISOR", "MANAGER", "CDH", "LTH", "ADMIN")) {
            for (String role : roles) {
                if (candidate.equalsIgnoreCase(role == null ? "" : role.trim())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public static List<String> ids(List<MailType> types) {
        return Arrays.stream(values())
                .filter(types::contains)
                .map(MailType::id)
                .toList();
    }
}
