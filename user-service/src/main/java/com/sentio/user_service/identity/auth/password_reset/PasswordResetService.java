package com.sentio.user_service.identity.auth.password_reset;

import com.sentio.user_service.identity.event.PasswordResetRequestedEvent;
import com.sentio.user_service.identity.user.api.dto.IssuedUserActionToken;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.identity.user.api.service.UserActionTokenService;
import com.sentio.user_service.refresh_token.api.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserActionTokenService userActionTokenService;
    private final UserAccountService userAccountService;
    private final PasswordResetProperties passwordResetProperties;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void requestReset(@NonNull String email, ZoneId userZone) {
        Optional<UserDto> userOpt = userAccountService.findActiveByEmail(email);

        if (userOpt.isEmpty()) {
            log.warn("Password reset requested for non-existing or inactive email: {}", email);
            return;
        }

        UserDto user = userOpt.get();

        IssuedUserActionToken issuedUserActionToken = userActionTokenService.issueToken(
                user.id(),
                UserActionTokenType.PASSWORD_RESET,
                passwordResetProperties.tokenTTL()
        );

        eventPublisher.publishEvent(
                PasswordResetRequestedEvent.builder()
                        .userId(user.id())
                        .email(user.email())
                        .firstName(user.firstName())
                        .rawToken(issuedUserActionToken.rawToken())
                        .expiresAt(issuedUserActionToken.expiresAt())
                        .resetUrl(passwordResetProperties.resetUrl())
                        .userZone(userZone)
                        .build()
        );
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        long userId = userActionTokenService.consumeToken(rawToken, UserActionTokenType.PASSWORD_RESET);
        userAccountService.updatePasswordHash(userId, passwordEncoder.encode(newPassword));

        // Whoever reset it now owns the account going forward - kill every other session
        // (e.g. an attacker who was already logged in with the old, compromised password).
        refreshTokenService.revokeAllActiveForUser(userId);
        log.info("Password reset completed for userId={}, all active sessions revoked", userId);
    }
}
