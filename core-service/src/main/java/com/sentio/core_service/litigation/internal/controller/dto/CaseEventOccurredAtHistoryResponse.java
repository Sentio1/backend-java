package com.sentio.core_service.litigation.internal.controller.dto;

import java.time.Instant;

public record CaseEventOccurredAtHistoryResponse(
        long caseEventId,
        long organizationId,
        Instant oldOccurredAt,
        Instant newOccurredAt,
        long changedBy,
        Instant changedAt,
        String reason
) {}
