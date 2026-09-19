package com.sentio.core_service.litigation.api.dto;

import com.sentio.core_service.litigation.api.enums.CaseInstance;
import com.sentio.core_service.litigation.api.enums.CaseStatus;
import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.court.api.dto.CourtResponse;
import java.time.Instant;
import java.time.LocalDate;

public record CaseResponse(
        long id,
        long organizationId,
        long responsibleUserId,
        String caseNumber,
        String internalNumber,
        String title,
        ProcedureType procedure,
        CaseInstance instance,
        CaseStatus status,
        CourtResponse court,
        String judgeName,
        LocalDate openedAt,
        LocalDate closedAt,
        boolean registryWatchEnabled,
        Instant registryLastCheckedAt,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
