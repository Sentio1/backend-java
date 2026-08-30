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
        // Посилання на повний текст у Mongo, не сам текст (SEN-69) — null для MANUAL.
        String registryDocumentTextRef,
        Long createdBy,
        Instant createdAt,
        // Заповнене лише якщо для eventCode/procedure/дати знайшлось активне DeadlineRule
        // (SEN-19 Deadline Engine) — null означає "подія не породила строк", а не помилку.
        Long deadlineId) {}
