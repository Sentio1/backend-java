package com.sentio.user_service.identity.organization.api.dto;

import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;

public record OrganizationMemberResponse(
        long orgId,
        OrgRole orgRole,
        boolean isDefault,
        UserContextResponse user
) {}
