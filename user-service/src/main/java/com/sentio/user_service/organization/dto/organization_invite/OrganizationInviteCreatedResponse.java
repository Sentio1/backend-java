package com.sentio.user_service.organization.dto.organization_invite;

import com.sentio.user_service.organization.enums.OrgRole;
import java.time.Instant;

/**
 * Returned only from the create-invite endpoint - the raw token is known exactly once, at creation
 * time (it's stored hashed and never recoverable afterward), so this shape is deliberately kept
 * separate from {@link OrganizationInviteResponse}, which backs the invite listing. That separation
 * makes it structurally impossible to wire the listing endpoint up to a response type carrying a
 * token by mistake.
 */
public record OrganizationInviteCreatedResponse(
        long id, long orgId, String email, OrgRole role, String token, Instant expiresAt) {}
