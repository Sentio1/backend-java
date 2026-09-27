package com.sentio.user_service.identity.organization.internal.controller;

import com.sentio.shared.dto.PageResponse;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberResponse;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.internal.OrganizationSecurity;
import com.sentio.user_service.identity.organization.internal.service.OrganizationServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Membership within an organization: listing members, changing roles, removing members. Every
 * endpoint here checks org access explicitly via {@link OrganizationSecurity} rather than
 * {@code @PreAuthorize} - a boolean @PreAuthorize expression can only ever deny with 403, and a
 * caller outside the organization must get 404 instead (see SEN-16: 403 confirms the resource
 * exists, which leaks that another org's data is there).
 */
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationMemberController {

    private final OrganizationServiceImpl organizationService;
    private final OrganizationSecurity organizationSecurity;

    @GetMapping("/{orgId}/members")
    public ResponseEntity<PageResponse<OrganizationMemberResponse>> getAllMembersOfOrganization(
            @PathVariable long orgId, final Pageable pageable, final Authentication authentication) {
        organizationSecurity.requireMembership(orgId, authentication);
        return ResponseEntity.ok(organizationService.getAllOrganizationMembers(orgId, pageable));
    }

    @PatchMapping("/{orgId}/members/{userId}/role")
    public ResponseEntity<OrganizationMemberResponse> patchRoleForMember(
            @PathVariable long orgId,
            @PathVariable long userId,
            @RequestBody OrgRole newRole,
            final Authentication authentication) {
        organizationSecurity.requireOwnership(orgId, authentication);
        return ResponseEntity.ok(organizationService.patchRoleForMember(orgId, userId, newRole));
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    public ResponseEntity<Void> deleteOrganizationMember(
            @PathVariable long orgId, @PathVariable long userId, final Authentication authentication) {
        organizationSecurity.requireOwnership(orgId, authentication);
        organizationService.deleteOrganizationMember(orgId, userId);
        return ResponseEntity.noContent().build();
    }
}
