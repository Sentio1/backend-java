package com.sentio.user_service.identity.auth.service;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtBlacklistService;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.identity.auth.dto.request.LoginRequest;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.dto.response.AuthResult;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import com.sentio.user_service.identity.auth.exception.RefreshTokenReusedException;
import com.sentio.user_service.identity.auth.oauth.GoogleAccountResolver;
import com.sentio.user_service.identity.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.identity.auth.token.TokenIssuer;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.service.OrganizationMemberService;
import com.sentio.user_service.identity.user.api.dto.NewLocalUser;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.identity.user.api.service.UserService;
import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.enums.RevokeReason;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static com.sentio.user_service.identity.auth.AuthConstants.*;

/**
 * Orchestrates the auth use cases (local register/login, Google sign-in, refresh, logout). Talks to
 * the user, organization and refresh_token modules only through their public {@code api}
 * interfaces, and delegates the mechanics to {@link GoogleAccountResolver} (who is this Google
 * user) and {@link TokenIssuer} (JWT + refresh token issuance) - this class only wires them
 * together and decides which one to call.
 *
 * <p>Every entry point ends up org-less for a brand new account: organization creation/joining is
 * a separate onboarding step (POST /organizations or an invite), the same for local and Google
 * sign-up.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserAccountService userAccountService;
    private final OrganizationMemberService organizationMemberService;
    private final RefreshTokenService refreshTokenService;

    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtBlacklistService jwtBlacklistService;

    private final AuthGuards authGuards;
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final PasswordVerifier passwordVerifier;
    private final GoogleAccountResolver googleAccountResolver;

    @Transactional
    public AuthResult register(RegistrationRequest request, String ip, String userAgent) {
        log.debug("Attempting to register user with email: {}", request.email());

        UserDto user = userAccountService.registerLocal(new NewLocalUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.phoneNumber(),
                request.firstName(),
                request.lastName(),
                request.middleName()));

        return new AuthResult(
                tokenIssuer.issue(user, null, ip, userAgent),
                userService.buildUserContext(user.id(), null));
    }

    @Transactional
    public AuthResult login(LoginRequest request, String ip, String userAgent) {
        log.debug("Attempting to login user with email: {}", request.email());

        Optional<UserDto> found = userAccountService.findActiveByEmail(request.email());
        // Always run the password check, even for an unknown email - see PasswordVerifier.
        boolean passwordMatches = passwordVerifier.matches(
                request.password(), found.map(UserDto::password).orElse(null));

        if (found.isEmpty() || !passwordMatches) {
            log.warn("Login failed: invalid credentials for email {}", request.email());
            throw new UnauthorizedException(INVALID_CREDENTIALS_MSG);
        }

        UserDto user = found.get();
        authGuards.assertNotServiceAccount(user, INVALID_CREDENTIALS_MSG);

        OrganizationMemberDto membership = findDefaultMembership(user.id());
        log.info("Successfully logged in user with email: {}", request.email());

        return new AuthResult(
                tokenIssuer.issue(user, membership, ip, userAgent),
                userService.buildUserContext(user.id(), membership));
    }

    @Transactional
    public AuthResult loginOrRegisterWithGoogle(GoogleIdentity identity, String ip, String userAgent) {
        log.debug("Attempting to login/register with Google for email: {}", identity.email());
        UserDto user = googleAccountResolver.resolveOrCreate(identity);

        authGuards.assertNotDeleted(user);
        authGuards.assertNotServiceAccount(user, INVALID_CREDENTIALS_MSG);

        OrganizationMemberDto membership = findDefaultMembership(user.id());
        log.info("Successfully logged in/registered Google user with email: {}", identity.email());

        return new AuthResult(
                tokenIssuer.issue(user, membership, ip, userAgent),
                userService.buildUserContext(user.id(), membership));
    }

    // noRollbackFor: on reuse, the family revocation must be committed even though
    // the request itself fails. Nothing else is written before that exception.
    @Transactional(noRollbackFor = RefreshTokenReusedException.class)
    public AuthTokens refresh(String rawToken, String ip, String userAgent) {
        log.debug("Attempting to refresh tokens");

        // Row-locked until commit: a concurrent refresh with the same token waits here
        // and then sees it already rotated (see RefreshTokenConcurrencyIT).
        RefreshTokenDto refreshToken = refreshTokenService.findByTokenHash(opaqueTokenService.hash(rawToken))
                .orElseThrow(() -> {
                    log.warn("Refresh failed: token not found");
                    return new UnauthorizedException(INVALID_REFRESH_TOKEN_MSG);
                });

        Instant now = Instant.now();
        if (refreshToken.revokedAt() != null) {
            handleRevokedTokenPresented(refreshToken, now);
        }
        if (refreshToken.expiresAt().isBefore(now)) {
            log.warn("Refresh failed: token has expired (userId: {})", refreshToken.userId());
            throw new UnauthorizedException("Refresh token has expired");
        }

        UserDto user = userAccountService.findActiveById(refreshToken.userId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MSG));
        authGuards.assertNotServiceAccount(user, INVALID_REFRESH_TOKEN_MSG);

        OrganizationMemberDto membership = findDefaultMembership(user.id());
        AuthTokens authTokens = tokenIssuer.rotate(user, membership, refreshToken, ip, userAgent);

        log.info("Successfully refreshed tokens for userId: {}", user.id());
        return authTokens;
    }

    private void handleRevokedTokenPresented(RefreshTokenDto refreshToken, Instant now) {
        boolean rotated = refreshToken.revokeReason() == RevokeReason.ROTATED;

        if (rotated && refreshToken.revokedAt().plus(REFRESH_REUSE_GRACE_PERIOD).isAfter(now)) {
            // Most likely two tabs refreshing at once - the other one already got the
            // new pair. Reject without punishing the session.
            log.info("Refresh rejected: token was rotated moments ago (userId: {})", refreshToken.userId());
            throw new UnauthorizedException("Refresh token has already been rotated");
        }

        if (rotated) {
            log.warn(
                    "SECURITY: rotated refresh token reused - revoking session family {} (userId: {})",
                    refreshToken.familyId(),
                    refreshToken.userId());
            refreshTokenService.revokeFamily(refreshToken.familyId(), RevokeReason.REUSE_DETECTED);
            throw new RefreshTokenReusedException("Refresh token has been revoked");
        }

        log.warn("Refresh failed: token has been revoked (userId: {}, reason: {})",
                refreshToken.userId(), refreshToken.revokeReason());
        throw new UnauthorizedException("Refresh token has been revoked");
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        log.debug("Attempting to logout user");
        if (refreshToken != null) {
            // Revoke the whole family, not just the one token: the session is over,
            // and a copy of an older (rotated) token must not be usable either.
            refreshTokenService.findByTokenHash(opaqueTokenService.hash(refreshToken))
                    .ifPresentOrElse(
                            rt -> {
                                refreshTokenService.revokeFamily(rt.familyId(), RevokeReason.LOGOUT);
                                log.info("Successfully revoked session on logout for userId: {}", rt.userId());
                            },
                            () -> log.debug("Refresh token not found during logout"));
        }

        if (accessToken != null) {
            revokeAccessToken(accessToken);
        }
    }

    /**
     * Puts the access token on the shared Redis blacklist until its natural expiry, so it stops
     * working here and in the Go services. Best effort: an unparseable/expired token is skipped.
     */
    public void revokeAccessToken(String accessToken) {
        try {
            jwtBlacklistService.addToBlacklist(
                    accessToken, jwtService.extractExpiration(accessToken).toEpochMilli());
        } catch (Exception e) {
            log.debug("Could not blacklist access token, skipping", e);
        }
    }

    private OrganizationMemberDto findDefaultMembership(long userId) {
        return organizationMemberService.findDefaultMembership(userId).orElse(null);
    }
}
