package com.lisovskyi.core_service.deadline.dto.response;

import com.lisovskyi.core_service.deadline.DeadlineStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record DeadlineResponse(
        Long id,
        Long caseId,
        Long triggeringEventId,
        Long ruleId,
        String title,
        String legalBasis,
        LocalDate startsOn,
        LocalDate dueOn,
        DeadlineStatus status,
        Instant completedAt,
        Long completedBy,
        LocalDate extendedTo,
        String note,
        String explanation
) {}
