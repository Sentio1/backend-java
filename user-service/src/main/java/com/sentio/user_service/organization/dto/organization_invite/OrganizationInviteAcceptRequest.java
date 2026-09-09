package com.sentio.user_service.organization.dto.organization_invite;

import jakarta.validation.constraints.NotBlank;

/** OrganizationInviteAcceptRequest record. */
public record OrganizationInviteAcceptRequest(@NotBlank String token) {}
