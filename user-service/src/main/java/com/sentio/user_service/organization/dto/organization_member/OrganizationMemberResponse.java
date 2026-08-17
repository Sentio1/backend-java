package com.sentio.user_service.organization.dto.organization_member;

import com.sentio.user_service.organization.enums.OrgRole;

public record OrganizationMemberResponse(
        long orgId,
        String orgName,
        OrgRole role,
        boolean isDefault
) {}
