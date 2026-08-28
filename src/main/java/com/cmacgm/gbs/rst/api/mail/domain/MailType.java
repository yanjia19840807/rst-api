package com.cmacgm.gbs.rst.api.mail.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Mail types a signed-in Supervisor / Manager / CDH / LTH can opt into.
 */
public enum MailType {
    APPROVAL_REQUESTED("approval.requested", "Awaiting my approval"),
    SUBMISSION_OUTCOME("submission.outcome", "Exercise returned, rejected or approved"),
    TIMESHEET_SYNC_FAILED("timesheet.sync.failed", "Timesheet sync failed");

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
     * @param raw slug or enum name
     * @return type, or null
     */
    public static MailType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if ("submission.returned".equals(trimmed)
                || "submission.rejected".equals(trimmed)
                || "submission.approved".equals(trimmed)
                || "SUBMISSION_RETURNED".equalsIgnoreCase(trimmed)
                || "SUBMISSION_REJECTED".equalsIgnoreCase(trimmed)
                || "SUBMISSION_APPROVED".equalsIgnoreCase(trimmed)) {
            return SUBMISSION_OUTCOME;
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
     * @param role SUPERVISOR / MANAGER / CDH / LTH
     * @return types, possibly empty
     */
    public static List<MailType> forRole(String role) {
        if (role == null) {
            return List.of();
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "SUPERVISOR" -> List.of(SUBMISSION_OUTCOME);
            case "MANAGER", "CDH" -> List.of(APPROVAL_REQUESTED);
            case "LTH" -> List.of(APPROVAL_REQUESTED, TIMESHEET_SYNC_FAILED);
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
        for (String candidate : List.of("SUPERVISOR", "MANAGER", "CDH", "LTH")) {
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
