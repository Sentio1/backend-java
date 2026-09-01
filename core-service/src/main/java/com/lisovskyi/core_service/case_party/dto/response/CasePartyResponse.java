package com.lisovskyi.core_service.case_party.dto.response;

import com.lisovskyi.core_service.case_party.CasePartyRole;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import java.time.Instant;

public record CasePartyResponse(
        Long id,
        Long caseId,
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
