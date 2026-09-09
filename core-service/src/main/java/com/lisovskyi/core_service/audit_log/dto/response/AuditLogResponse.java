package com.lisovskyi.core_service.audit_log.dto.response;

import com.lisovskyi.core_service.audit_log.enums.ChangedByType;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import java.time.Instant;

public record AuditLogResponse(
        long id,
        long organizationId,
        EntityType entityType,
        long entityId,
        String fieldName,
        String oldValue,
        String newValue,
        Long changedBy,
        ChangedByType changedByType,
        Instant changedAt
) {}
