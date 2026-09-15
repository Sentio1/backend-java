package com.sentio.user_service.identity.organization.api.dto;

import com.sentio.user_service.identity.organization.api.enums.OrgRole;

import java.time.Instant;

public record OrganizationInviteAcceptResponse(
        long orgId,
        String organizationName,
        OrgRole role,
        boolean isDefault,
        Instant joinedAt
) {}
