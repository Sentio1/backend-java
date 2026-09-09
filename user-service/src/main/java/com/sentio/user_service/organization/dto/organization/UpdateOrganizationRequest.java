package com.sentio.user_service.organization.dto.organization;

import static com.sentio.user_service.organization.OrganizationConstants.NAME_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** UpdateOrganizationRequest record. */
public record UpdateOrganizationRequest(
        @NotBlank(message = "Organization name must not be blank")
        @Size(max = NAME_LENGTH, message = "Organization name must not exceed 255 characters")
        String name) {}
