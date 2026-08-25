package com.lisovskyi.core_service.client.dto.response;

import com.lisovskyi.core_service.client.ClientType;

import java.time.LocalDate;

public record ClientResponse(
        Long id,
        Long organizationId,
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
        String email,
        String phoneNumber,
        String address,
        String notes,
        Long createdBy
) {}
