package com.lisovskyi.core_service.case_.dto.response;

import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.court.dto.response.CourtResponse;
import java.time.Instant;
import java.time.LocalDate;

public record CaseResponse(
        Long id,
        Long organizationId,
        Long responsibleUserId,
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
        Instant updatedAt) {}
