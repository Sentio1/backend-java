package com.sentio.user_service.identity.organization.internal.controller.dto.organization;

import static com.sentio.user_service.identity.organization.internal.OrganizationConstants.NAME_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @NotBlank(message = "Organization name must not be blank")
        @Size(max = NAME_LENGTH, message = "Organization name must not exceed 255 characters")
        String name
) {}
