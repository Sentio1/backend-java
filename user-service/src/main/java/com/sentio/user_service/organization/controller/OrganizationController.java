package com.sentio.user_service.organization.controller;

import com.lisovskyi.security.autoconfigure.security.annotation.CurrentUser;
import com.sentio.user_service.auth.cookie.AuthCookieService;
import com.sentio.user_service.auth.dto.AuthResult;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.organization.OrganizationSecurity;
import com.sentio.user_service.organization.dto.CreateOrganizationRequest;
import com.sentio.user_service.organization.dto.organization.OrganizationResponse;
import com.sentio.user_service.organization.dto.organization.UpdateOrganizationRequest;
import com.sentio.user_service.organization.service.OrganizationService;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The organization itself - creating a session in a different default org (switch) and editing the
 * organization's own fields. Membership management lives in {@link OrganizationMemberController},
 * invites in {@link OrganizationInviteController}.
 */
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final AuthCookieService authCookieService;
    private final OrganizationSecurity organizationSecurity;

    @PostMapping
    public ResponseEntity<UserContextResponse> createOrganization(
            @RequestBody @Valid CreateOrganizationRequest request,
            @CurrentUser SecurityUser user,
            final HttpServletRequest httpRequest,
            final HttpServletResponse response) {
        AuthResult authResult = organizationService.createOrganization(
                user.getUser(),
                request,
                HttpRequestUtils.getClientIP(httpRequest),
                httpRequest.getHeader("User-Agent"));
        authCookieService.setCookies(response, authResult.authTokens());

        return ResponseEntity.ok(authResult.userContext());
    }

    // Not membership-gated on purpose: switchDefaultOrganization itself looks the
    // membership up and 404s if the caller isn't in orgId - no separate check needed.
    @PostMapping("/{orgId}/switch")
    public ResponseEntity<UserContextResponse> switchOrganization(
            @PathVariable long orgId,
            @CurrentUser SecurityUser user,
            final HttpServletRequest httpRequest,
            final HttpServletResponse response) {
        AuthResult authResult = organizationService.switchDefaultOrganization(
                user.getId(), orgId, HttpRequestUtils.getClientIP(httpRequest), httpRequest.getHeader("User-Agent"));
        AuthTokens tokens = authResult.authTokens();
        UserContextResponse userContext = authResult.userContext();

        authCookieService.setCookies(response, tokens);

        return ResponseEntity.ok(userContext);
    }

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
