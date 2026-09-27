package com.sentio.user_service.identity.organization.internal.controller.dto.organization_invite;

import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** OrganizationInviteRequest record. */
public record OrganizationInviteRequest(
        @NotBlank @Email String email, @NotNull OrgRole role) {}
