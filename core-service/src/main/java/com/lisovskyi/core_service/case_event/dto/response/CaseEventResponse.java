package com.lisovskyi.core_service.case_event.dto.response;

import com.lisovskyi.core_service.case_event.enums.DeadlineResolution;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.case_event.enums.Source;
import java.time.Instant;
import java.util.List;

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
        // Список, а не одне значення (SEN-29 AC1) — подія може одночасно підпадати під кілька
        // застосовних DeadlineRule (SEN-19 Deadline Engine) і породжувати дедлайн на кожне.
        // Порожній список означає "подія не породила жодного строку" — той самий факт, що явно
        // піднятий у deadlineResolution нижче, а не помилку.
        List<Long> deadlineIds,
        // SEN-29 AC2: явний стан для картки справи — не змушує фронт здогадуватись по
        // порожньому/непорожньому deadlineIds, і розрізняє "нема правила" від "юрист відхилив".
        DeadlineResolution deadlineResolution) {}
