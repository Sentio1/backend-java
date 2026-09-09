package com.sentio.user_service.organization.dto.organization_member;

import com.sentio.user_service.user.dto.UserContextResponse;

/** OrganizationMemberResponse record. */
public record OrganizationMemberResponse(long orgId, boolean isDefault, UserContextResponse user) {}
