package com.sentio.core_service.deadline.internal.controller.dto;

import com.sentio.core_service.deadline.internal.enums.DeadlineSource;
import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record DeadlineResponse(
        long id,
        long caseId,
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
