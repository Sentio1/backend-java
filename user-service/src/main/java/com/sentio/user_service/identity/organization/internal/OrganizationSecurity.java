package com.sentio.user_service.identity.organization.internal;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.internal.entity.OrganizationMember;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationMemberRepository;
import com.sentio.user_service.identity.user.api.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Backs {@code @PreAuthorize("@organizationSecurity.canManage(#id, authentication)")} on
 * org-mutating endpoints. Checks live membership in the DB rather than any org_id baked into the
 * JWT - JwtAuthFilter reloads the principal by user id on every request and never surfaces JWT
 * claims onto it, so there's nothing to read off the token here anyway. Checking the DB also means
 * a member removed from an org loses access immediately, not only after their access token expires.
 */
@Component("organizationSecurity")
@RequiredArgsConstructor
public class OrganizationSecurity {

    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationMember requireMembership(long organizationId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }

        return organizationMemberRepository
                .findByUserIdAndOrganizationId(securityUser.getId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
    }

    public void requireOwnership(long organizationId, Authentication authentication) {
        OrganizationMember member = requireMembership(organizationId, authentication);
        if (member.getRole() != OrgRole.OWNER) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
    }
}
