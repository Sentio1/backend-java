package com.sentio.user_service.auth.token;

import com.lisovskyi.security.autoconfigure.security.jwt.JwtProperties;
import com.lisovskyi.security.autoconfigure.security.jwt.JwtService;
import com.lisovskyi.security.autoconfigure.security.jwt.OpaqueTokenService;
import com.sentio.user_service.auth.dto.AuthTokens;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.enums.PlatformRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Issues an access/refresh token pair for an authenticated user within a given
 * organization membership. The only place that knows how JWT claims are shaped
 * and how refresh tokens are persisted - local login, Google login and token
 * refresh all go through this.
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthTokens issue(User user, @Nullable OrganizationMember membership) {
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

        RefreshToken refreshTokenInstance = RefreshToken.builder()
                .tokenHash(hashedRefreshToken)
                .user(user)
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpiration(), ChronoUnit.MILLIS))
                .build();

        refreshTokenRepository.save(refreshTokenInstance);

        return new AuthTokens(accessToken, refreshToken);
    }
}
