package com.lisovskyi.core_service.case_event.dto.response;

import java.time.Instant;

public record CaseEventOccurredAtHistoryResponse(
        long caseEventId,
        long organizationId,
        Instant oldOccurredAt,
        Instant newOccurredAt,
        long changedBy,
        Instant changedAt,
        String reason) {}
