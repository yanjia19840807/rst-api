package com.cmacgm.gbs.rst.api.workflow.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;

/**
 * Workflow action view.
 */
public record ActionView(
        short stepNo,
        String actionType,
        String actorCcgid,
        String actorRoleCode,
        String actorDisplayName,
        String comments,
        Instant actionAt,
        UUID requestId,
        AuditActorView actedBy) {
}
