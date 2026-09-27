package com.sentio.user_service.identity.organization.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OrganizationInviteAcceptRequest(@NotBlank String token) {}
