package com.sentio.user_service.identity.auth.controller;

import com.lisovskyi.security.autoconfigure.security.annotation.CurrentUser;
import com.sentio.user_service.identity.auth.cookie.AuthCookieService;
import com.sentio.user_service.identity.auth.service.AccessTokenReissuer;
import com.sentio.user_service.identity.auth.service.AuthService;
import com.sentio.user_service.identity.organization.api.dto.CreateOrganizationRequest;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteAcceptRequest;
import com.sentio.user_service.identity.organization.api.dto.OrganizationInviteAcceptResponse;
import com.sentio.user_service.identity.organization.api.service.OrganizationInviteService;
import com.sentio.user_service.identity.organization.api.service.OrganizationService;
import com.sentio.user_service.identity.user.api.SecurityUser;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.service.UserDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints under /organizations and /users that change what the caller's token says (default
 * org) or end the account. They live in auth, not in organization/user, on purpose: auth depends
 * on those modules' APIs, so if they called back into auth for cookies/tokens the modules would
 * form a cycle. Here the dependency only ever points auth -> identity. The URLs are unchanged.
 */
@RestController
@RequiredArgsConstructor
public class SessionChangingController {

    private final OrganizationService organizationService;
    private final OrganizationInviteService organizationInviteService;
    private final UserDeletionService userDeletionService;
    private final AccessTokenReissuer accessTokenReissuer;
    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @PostMapping("/organizations")
    public ResponseEntity<UserContextResponse> createOrganization(
            @RequestBody @Valid CreateOrganizationRequest request,
            @CurrentUser SecurityUser user,
            final HttpServletResponse response) {
        organizationService.createOrganization(user.getId(), request);
        return ResponseEntity.ok(accessTokenReissuer.reissue(user.getId(), response));
    }

    // Not membership-gated on purpose: switchDefaultOrganization itself looks the
    // membership up and 404s if the caller isn't in orgId - no separate check needed.
    @PostMapping("/organizations/{orgId}/switch")
    public ResponseEntity<UserContextResponse> switchOrganization(
            @PathVariable long orgId,
            @CurrentUser SecurityUser user,
            final HttpServletResponse response) {
        organizationService.switchDefaultOrganization(user.getId(), orgId);
        return ResponseEntity.ok(accessTokenReissuer.reissue(user.getId(), response));
    }

    // The first accepted invite becomes the default org - without a fresh access
    // token the client would stay org-less until the next /auth/refresh.
    @PostMapping("/users/me/invites/accept")
    public ResponseEntity<OrganizationInviteAcceptResponse> acceptOrganizationInvite(
            @RequestBody @Valid OrganizationInviteAcceptRequest request,
            @CurrentUser SecurityUser user,
            final HttpServletResponse response) {
        OrganizationInviteAcceptResponse accepted = organizationInviteService.acceptInvite(request.token(), user.getId());
        accessTokenReissuer.reissue(user.getId(), response);
        return ResponseEntity.ok(accepted);
    }

    // Sessions are revoked by the deletion itself; the current access token is
    // blacklisted here (only auth sees raw tokens) and the cookies are cleared.
    @DeleteMapping("/users/me")
    public ResponseEntity<Void> deleteAccount(
            @CurrentUser SecurityUser user,
            final HttpServletRequest request,
            final HttpServletResponse response) {
        userDeletionService.deleteUser(user.getId());
        authCookieService.readAccessToken(request).ifPresent(authService::revokeAccessToken);
        authCookieService.clearCookies(response);
        return ResponseEntity.noContent().build();
    }
}
