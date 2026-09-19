package com.sentio.user_service.identity.auth.service;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.identity.auth.cookie.AuthCookieService;
import com.sentio.user_service.identity.auth.token.TokenIssuer;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.service.OrganizationMemberService;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.identity.user.api.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-mints the caller's access token (and its cookie) after their default organization changed
 * (organization created, switched, first invite accepted).
 *
 * <p>Only the access token, not a refresh-token rotation: the refresh cookie is scoped to {@code
 * /api/v1/auth} on purpose, so it never even reaches {@code /organizations/**} - and it doesn't
 * need to. The session isn't tied to an organization, only the access token's claims (org_id,
 * roles) are, and every later /auth/refresh re-reads the default membership anyway. So no new
 * session is started, nothing is left dangling and the active-session limit isn't touched.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccessTokenReissuer {

    private final UserAccountService userAccountService;
    private final UserService userService;
    private final OrganizationMemberService organizationMemberService;
    private final AuthGuards authGuards;
    private final TokenIssuer tokenIssuer;
    private final AuthCookieService authCookieService;

    @Transactional(readOnly = true)
    public UserContextResponse reissue(long userId, HttpServletResponse response) {
        UserDto user = userAccountService.findActiveById(userId)
                .orElseThrow(() -> new UnauthorizedException("User account is not active"));
        authGuards.assertNotServiceAccount(user, "Service accounts have no interactive session");

        OrganizationMemberDto membership = organizationMemberService.findDefaultMembership(userId).orElse(null);
        authCookieService.setAccessToken(response, tokenIssuer.issueAccessToken(user, membership));

        log.debug("Re-issued access token for userId: {} (orgId: {})",
                userId, membership != null ? membership.organizationId() : null);
        return userService.buildUserContext(userId, membership);
    }
}
