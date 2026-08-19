package com.sentio.user_service.auth.token;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenConstants;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.enums.PlatformRole;
import jakarta.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Issues an access/refresh token pair for an authenticated user within a given organization
 * membership. The only place that knows how JWT claims are shaped and how refresh tokens are
 * persisted - local login, Google login and token refresh all go through this.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenIssuer {

    private final JwtProperties jwtProperties;
    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthTokens issue(User user, @Nullable OrganizationMember membership, String ip, String userAgent) {
        Map<String, Object> extraClaims = new HashMap<>();
        List<String> roles = new ArrayList<>();

        if (membership != null) {
            extraClaims.put("org_id", membership.getOrganization().getId());
            roles.add(membership.getRole().name());
        }

        if (user.getPlatformRole() == PlatformRole.ADMIN) {
            roles.add("ADMIN");
        }

        if (!roles.isEmpty()) {
            extraClaims.put("roles", roles);
        }

        SecurityUser securityUser = new SecurityUser(user);
        String accessToken = jwtService.generateToken(securityUser, extraClaims);
        String refreshToken = opaqueTokenService.generate();
        String hashedRefreshToken = opaqueTokenService.hash(refreshToken);

        enforceActiveSessionLimit(user.getId());

        RefreshToken refreshTokenInstance = RefreshToken.builder()
                .tokenHash(hashedRefreshToken)
                .user(user)
                .userAgent(userAgent)
                .ip(parseIp(ip))
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpiration(), ChronoUnit.MILLIS))
                .build();

        refreshTokenRepository.save(refreshTokenInstance);

        return new AuthTokens(accessToken, refreshToken);
    }

    // Caps unbounded growth of refresh_tokens per user: revoking (not deleting) the
    // oldest active sessions once the new one would push the count past the limit -
    // same "Active sessions" concept as Telegram/Google's "log out other devices",
    // just applied eagerly instead of waiting for the user to do it themselves.
    private void enforceActiveSessionLimit(long userId) {
        List<RefreshToken> activeSessions =
                refreshTokenRepository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId);

        int overLimitBy = activeSessions.size() - RefreshTokenConstants.MAX_ACTIVE_SESSIONS + 1;
        if (overLimitBy <= 0) {
            return;
        }

        Instant now = Instant.now();
        List<RefreshToken> oldest = activeSessions.subList(0, overLimitBy);
        oldest.forEach(session -> session.setRevokedAt(now));
        refreshTokenRepository.saveAll(oldest);
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
