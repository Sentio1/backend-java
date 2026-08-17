package com.sentio.user_service.organization.dto.organization_invite;

import com.sentio.user_service.organization.enums.OrgRole;

import java.time.Instant;

public record OrganizationInviteAcceptResponse(
        long orgId,
        String organizationName,
        OrgRole role,
        boolean isDefault,
        Instant joinedAt
) {}
