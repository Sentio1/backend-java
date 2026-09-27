package com.sentio.core_service.audit.api.dto;

import com.sentio.core_service.audit.api.enums.ChangedByType;
import com.sentio.core_service.audit.api.enums.EntityType;
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
