package com.sentio.user_service.identity.auth.token;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.user.api.SecurityUser;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Issues access/refresh token pairs. The only place that knows how JWT claims are shaped - local
 * login, Google login, token refresh and service tokens all go through this. Refresh token
 * persistence itself is owned entirely by {@link RefreshTokenService}.
 *
 * <p>Session lifetime: every refresh token lives {@code app.jwt.refresh-token-expiration} from
 * its own issue (sliding window - an active user keeps getting pushed forward), but a session
 * (token family) never outlives {@code app.session.absolute-lifetime} from the original login.
 */
@Component
@Slf4j
public class TokenIssuer {

    private final JwtProperties jwtProperties;
    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Duration sessionAbsoluteLifetime;

    public TokenIssuer(
            JwtProperties jwtProperties,
            JwtService jwtService,
            OpaqueTokenService opaqueTokenService,
            RefreshTokenService refreshTokenService,
            @Value("${app.session.absolute-lifetime:30d}") Duration sessionAbsoluteLifetime) {
        this.jwtProperties = jwtProperties;
        this.jwtService = jwtService;
        this.opaqueTokenService = opaqueTokenService;
        this.refreshTokenService = refreshTokenService;
        this.sessionAbsoluteLifetime = sessionAbsoluteLifetime;
    }

    // Лише access token, без refresh token/сесії.
    public String issueServiceAccessToken(UserDto user) {
        return jwtService.generateToken(SecurityUser.from(user), createExtraClaims(user, null));
    }

    /** RFC 6749 {@code expires_in} - seconds, not the millis JwtProperties stores. */
    public long accessTokenTtlSeconds() {
        return Duration.ofMillis(jwtProperties.getAccessTokenExpiration()).toSeconds();
    }

    /** A brand new session (login / registration / Google sign-in): a new token family. */
    public AuthTokens issue(UserDto user, @Nullable OrganizationMemberDto membership, String ip, String userAgent) {
        String refreshToken = opaqueTokenService.generate();
        Instant now = Instant.now();

        refreshTokenService.enforceActiveSessionLimit(user.id());
        refreshTokenService.startSession(
                user.id(),
                opaqueTokenService.hash(refreshToken),
                userAgent,
                parseIp(ip),
                refreshTokenExpiry(now),
                now.plus(sessionAbsoluteLifetime));

        return new AuthTokens(accessToken(user, membership), refreshToken);
    }

    /**
     * Continues an existing session: revokes {@code current} as rotated and issues its successor in
     * the same family. Doesn't count against the active-session limit - it's the same session.
     */
    public AuthTokens rotate(
            UserDto user,
            @Nullable OrganizationMemberDto membership,
            RefreshTokenDto current,
            String ip,
            String userAgent) {
        String refreshToken = opaqueTokenService.generate();

        refreshTokenService.rotate(
                current,
                opaqueTokenService.hash(refreshToken),
                userAgent,
                parseIp(ip),
                refreshTokenExpiry(Instant.now()));

        return new AuthTokens(accessToken(user, membership), refreshToken);
    }

    /** Access token only - for when the claims changed but the session (refresh token) didn't. */
    public String issueAccessToken(UserDto user, @Nullable OrganizationMemberDto membership) {
        return accessToken(user, membership);
    }

    private String accessToken(UserDto user, @Nullable OrganizationMemberDto membership) {
        return jwtService.generateToken(SecurityUser.from(user), createExtraClaims(user, membership));
    }

    private Instant refreshTokenExpiry(Instant from) {
        return from.plusMillis(jwtProperties.getRefreshTokenExpiration());
    }

    private @NonNull Map<String, Object> createExtraClaims(UserDto user, @Nullable OrganizationMemberDto membership) {
        Map<String, Object> extraClaims = new HashMap<>();
        List<String> roles = new ArrayList<>();

        if (membership != null) {
            extraClaims.put("org_id", membership.organizationId());
            roles.add(membership.orgRole().name());
        }

        if (user.platformRole() == PlatformRole.ADMIN || user.platformRole() == PlatformRole.SERVICE) {
            roles.add(user.platformRole().name());
        }

        if (!roles.isEmpty()) {
            extraClaims.put("roles", roles);
        }
        return extraClaims;
    }

    // ip is attacker/network-controlled input (see HttpRequestUtils.getClientIP) -
    // malformed values must not blow up login/register, so this logs and stores
    // null on failure rather than throwing.
    private InetAddress parseIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        try {
            // ofLiteral, not getByName: getByName would resolve anything that isn't an
            // IP literal via DNS - a network call on attacker-influenced input.
            return InetAddress.ofLiteral(ip);
        } catch (IllegalArgumentException e) {
            log.debug("Could not parse client IP '{}' for refresh token, storing null", ip, e);
            return null;
        }
    }
}
