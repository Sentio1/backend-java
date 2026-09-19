package com.sentio.user_service.identity.organization.api.dto;

import com.sentio.user_service.identity.organization.api.enums.OrgRole;

public record OrganizationMemberDto(
        long id,
        long organizationId,
        String organizationName,
        OrgRole orgRole
) {}
