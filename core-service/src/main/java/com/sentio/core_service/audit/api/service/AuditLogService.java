package com.sentio.core_service.audit.api.service;

import com.sentio.core_service.audit.api.dto.AuditLogResponse;
import com.sentio.core_service.audit.api.enums.EntityType;
import com.sentio.shared.entity.id.EntityId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Append-only field-change history. Both write methods require an existing transaction
 * (Propagation.MANDATORY): an audit row must commit or roll back together with the change it
 * describes.
 */
public interface AuditLogService {

    /** A change made by a person - changedBy is a real auth.users.id. */
    void log(
            OrganizationId organizationId,
            EntityType entityType,
            EntityId entityId,
            UserId changedBy,
            String fieldName,
            String oldValue,
            String newValue);

    /** A change made by an automatic process nobody is personally responsible for. */
    void logSystemChange(
            OrganizationId organizationId,
            EntityType entityType,
            EntityId entityId,
            String fieldName,
            String oldValue,
            String newValue);

    /**
     * One timeline for a whole case: rows of the case itself plus rows of the given case events and
     * deadlines. The caller (the module that owns cases) resolves which events/deadlines belong to
     * the case - this module only knows entity ids.
     */
    Page<AuditLogResponse> findCaseHistory(
            long organizationId,
            long caseId,
            Collection<Long> caseEventIds,
            Collection<Long> deadlineIds,
            Pageable pageable);
}
