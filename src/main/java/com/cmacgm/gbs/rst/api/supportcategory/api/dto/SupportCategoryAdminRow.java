package com.cmacgm.gbs.rst.api.supportcategory.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;

/**
 * One catalog row for the Admin Support Category page.
 *
 * @param id category id
 * @param name display name
 * @param status ACTIVE or INACTIVE
 * @param displayOrder sort key
 * @param createdBy first CREATE actor
 * @param createdAt created at
 * @param updatedAt last change
 */
public record SupportCategoryAdminRow(
        UUID id,
        String name,
        String status,
        int displayOrder,
        AuditActorView createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
