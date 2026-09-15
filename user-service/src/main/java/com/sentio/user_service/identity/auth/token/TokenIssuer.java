package com.sentio.user_service.identity.auth.token;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.sentio.user_service.identity.auth.dto.response.AuthTokens;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import com.sentio.user_service.identity.user.api.SecurityUser;
import com.sentio.user_service.identity.user.api.service.UserService;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import jakarta.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Issues an access/refresh token pair for an authenticated user within a given organization
 * membership. The only place that knows how JWT claims are shaped - local login, Google login and
 * token refresh all go through this. Refresh token persistence itself is owned entirely by {@link
 * RefreshTokenService}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtProperties jwtProperties;
    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    // Лише access token, без refresh token/сесії.
    public String issueServiceAccessToken(long userId) {
        UserDto user = userService.findUserById(userId);
        SecurityUser securityUser = SecurityUser.from(user);
        return jwtService.generateToken(securityUser, createExtraClaims(user, null));
    }

    public AuthTokens issue(UserDto user, @Nullable OrganizationMemberDto membership, String ip, String userAgent) {
        Map<String, Object> extraClaims = createExtraClaims(user, membership);

        SecurityUser securityUser = SecurityUser.from(user);
        String accessToken = jwtService.generateToken(securityUser, extraClaims);
        String refreshToken = opaqueTokenService.generate();
        String hashedRefreshToken = opaqueTokenService.hash(refreshToken);

        refreshTokenService.enforceActiveSessionLimit(user.id());

        refreshTokenService.issue(
                user.id(),
                hashedRefreshToken,
                userAgent,
                parseIp(ip),
                Instant.now().plus(jwtProperties.getRefreshTokenExpiration(), ChronoUnit.MILLIS));

        return new AuthTokens(accessToken, refreshToken);
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
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            log.debug("Could not parse client IP '{}' for refresh token, storing null", ip, e);
            return null;
        }
    }
}
