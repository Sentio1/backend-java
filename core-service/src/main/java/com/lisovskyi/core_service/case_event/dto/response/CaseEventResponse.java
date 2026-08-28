package com.lisovskyi.core_service.case_event.dto.response;

import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.case_event.enums.Source;

import java.time.Instant;

public record CaseEventResponse(
        Long id,
        Long caseId,
        EventCode eventCode,
        String title,
        String description,
        Instant occurredAt,
        Instant registeredAt,
        Source source,
        Long registryDocumentId,
        Long createdBy,
        Instant createdAt) {}
