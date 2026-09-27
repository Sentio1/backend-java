package com.sentio.user_service.identity.organization.api.dto;

import com.sentio.user_service.identity.organization.api.enums.OrgRole;

import java.time.Instant;

public record OrganizationInviteResponse(
        long id,
        long orgId,
        String email,
        OrgRole role,
        Instant expiresAt
) {}
