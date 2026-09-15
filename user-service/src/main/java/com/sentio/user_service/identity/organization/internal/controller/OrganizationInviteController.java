package com.sentio.user_service.identity.organization.internal.controller;

import com.lisovskyi.security.autoconfigure.security.annotation.CurrentUser;
import com.sentio.shared.dto.PageResponse;
import com.sentio.user_service.identity.organization.internal.OrganizationSecurity;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization_invite.OrganizationInviteCreatedResponse;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization_invite.OrganizationInviteRequest;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteResponse;
import com.sentio.user_service.identity.organization.internal.service.OrganizationInviteServiceImpl;
import com.sentio.user_service.identity.user.api.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invite management: OWNER-only creation and listing, scoped to a specific org, plus a self-service
 * accept that isn't org-scoped at all - the invite token itself identifies which org it's for, and
 * any authenticated user can redeem their own invite regardless of what orgs they already belong
 * to.
 */
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationInviteController {

    private final OrganizationInviteServiceImpl organizationInviteService;
    private final OrganizationSecurity organizationSecurity;

    @PostMapping("/{orgId}/invites")
    public ResponseEntity<OrganizationInviteCreatedResponse> inviteUserToOrganization(
            @RequestBody @Valid OrganizationInviteRequest organizationInviteRequest,
            @PathVariable long orgId,
            @CurrentUser SecurityUser currentUser,
            final Authentication authentication) {
        organizationSecurity.requireOwnership(orgId, authentication);

        OrganizationInviteCreatedResponse response = organizationInviteService.inviteUserToOrganization(
                organizationInviteRequest, orgId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orgId}/invites")
    public ResponseEntity<PageResponse<OrganizationInviteResponse>> getAllInvitesForOrganization(
            @PathVariable long orgId, final Pageable pageable, final Authentication authentication) {
        organizationSecurity.requireOwnership(orgId, authentication);
        return ResponseEntity.ok(organizationInviteService.getAllInvites(orgId, pageable));
    }

    @DeleteMapping("/{orgId}/invites/{inviteId}")
    public ResponseEntity<OrganizationInviteResponse> revokeInvite(
            @PathVariable long orgId, @PathVariable long inviteId, final Authentication authentication) {
        organizationSecurity.requireOwnership(orgId, authentication);
        return ResponseEntity.ok(organizationInviteService.revokeInvite(orgId, inviteId));
    }
}
