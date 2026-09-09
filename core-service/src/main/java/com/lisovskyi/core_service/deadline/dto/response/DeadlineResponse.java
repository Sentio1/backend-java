package com.lisovskyi.core_service.deadline.dto.response;

import com.lisovskyi.core_service.deadline.enums.DeadlineSource;
import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
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
        DeadlineSource source,
        LocalDate startsOn,
        LocalDate dueOn,
        DeadlineStatus status,
        Instant completedAt,
        Long completedBy,
        LocalDate extendedTo,
        String note,
        String explanation,
        Instant rejectedAt,
        Long rejectedBy,
        String rejectionReason,
        Long createdBy
) {}
