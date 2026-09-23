package com.cmacgm.gbs.rst.api.audit.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.audit.domain.AuditAction;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only audit events.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * @param ids event ids
     * @return matching rows
     */
    List<AuditEvent> findByIdIn(Collection<UUID> ids);

    /**
     * @param entityType aggregate
     * @param action recorded action
     * @param entityIds business ids
     * @return matching rows
     */
    List<AuditEvent> findByEntityTypeAndActionAndEntityIdIn(
            AuditEntityType entityType, AuditAction action, Collection<UUID> entityIds);
}
