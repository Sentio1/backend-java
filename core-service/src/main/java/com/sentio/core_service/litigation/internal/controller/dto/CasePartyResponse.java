package com.sentio.core_service.litigation.internal.controller.dto;

import com.sentio.core_service.litigation.internal.enums.CasePartyRole;
import com.sentio.core_service.client.api.dto.ClientResponse;
import java.time.Instant;

public record CasePartyResponse(
        long id,
        long caseId,
        Long organizationId,
        CasePartyRole role,
        boolean isClient,
        boolean isPrimary,
        ClientResponse client,
        String opponentName,
        String opponentContact,
        String opponentDetails,
        Instant createdAt,
        Instant updatedAt) {}
