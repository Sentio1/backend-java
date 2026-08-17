package com.sentio.user_service.organization.dto.organization_invite;

import com.sentio.user_service.organization.enums.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrganizationInviteRequest(
        @NotBlank @Email String email,
        @NotNull OrgRole role
) {}
