package com.sentio.user_service.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.sentio.user_service.organization.OrganizationConstants.NAME_LENGTH;

public record UpdateOrganizationRequest(
        @NotBlank(message = "Organization name must not be blank")
        @Size(max = NAME_LENGTH, message = "Organization name must not exceed 255 characters")
        String name
) {}
