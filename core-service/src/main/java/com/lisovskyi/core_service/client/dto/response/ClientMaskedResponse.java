package com.lisovskyi.core_service.client.dto.response;

import com.lisovskyi.core_service.client.ClientType;
import java.time.Instant;
import java.util.List;

public record ClientMaskedResponse(
        Long id,
        Long organizationId,
        ClientType type,

        String lastName,
        String firstName,
        String middleName,
        String birthDate,
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
        Instant updatedAt) {}
