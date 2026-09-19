package com.sentio.core_service.client.api.dto;

import com.sentio.core_service.client.api.enums.ClientType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClientResponse(
        long id,
        long organizationId,
        ClientType type,

        String lastName,
        String firstName,
        String middleName,
        LocalDate birthDate,
        String rnokpp,
        String passport,

        String companyName,
        String edrpou,
        String directorName,
        String contactPersonName,

        String email,
        String phoneNumber,
        String address,
        String notes,
        List<String> activities,

        Long createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
