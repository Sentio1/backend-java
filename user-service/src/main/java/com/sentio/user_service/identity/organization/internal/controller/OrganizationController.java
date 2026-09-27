package com.sentio.user_service.identity.organization.internal.controller;

import com.sentio.user_service.identity.organization.internal.OrganizationSecurity;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.OrganizationResponse;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.UpdateOrganizationRequest;
import com.sentio.user_service.identity.organization.internal.service.OrganizationServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * The organization's own fields. Creating an organization and switching the default one change the
 * caller's access token, so those endpoints live in auth (SessionChangingController). Membership
 * management lives in {@link OrganizationMemberController}, invites in {@link
 * OrganizationInviteController}.
 */
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationServiceImpl organizationService;
    private final OrganizationSecurity organizationSecurity;

    @PutMapping("/{orgId}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable long orgId,
            @RequestBody @Valid UpdateOrganizationRequest updateRequest,
            final Authentication authentication) {
        organizationSecurity.requireOwnership(orgId, authentication);

        OrganizationResponse organizationResponse = organizationService.updateOrganization(orgId, updateRequest);
        return ResponseEntity.ok(organizationResponse);
    }
}
