package com.lisovskyi.core_service.audit_log.dto.response;

import com.lisovskyi.core_service.audit_log.EntityType;
import java.time.Instant;

public record AuditLogResponse(
        long id,
        long organizationId,
        EntityType entityType,
        long entityId,
        String fieldName,
        String oldValue,
        String newValue,
        long changedBy,
        Instant changedAt
) {}
