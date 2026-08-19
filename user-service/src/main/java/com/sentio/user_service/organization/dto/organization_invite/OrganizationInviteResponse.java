package com.sentio.user_service.organization.dto.organization_invite;

import com.sentio.user_service.organization.enums.OrgRole;
import java.time.Instant;

/** OrganizationInviteResponse record. */
public record OrganizationInviteResponse(long id, long orgId, String email, OrgRole role, Instant expiresAt) {}
