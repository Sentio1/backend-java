package com.sentio.user_service.identity.auth.verification;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.identity.event.EmailVerificationRequestedEvent;
import com.sentio.user_service.identity.user.api.dto.IssuedUserActionToken;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.identity.user.api.service.UserActionTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserActionTokenService userActionTokenService;
    private final UserAccountService userAccountService;
    private final EmailVerificationProperties emailVerificationProperties;

    @Transactional
    public void sendVerificationEmail(@NonNull Long currentUserId) {
        UserDto user = userAccountService.findActiveById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id %d was not found".formatted(currentUserId)));

        if (user.emailVerified()) {
            log.info("Email already verified for user id={}", currentUserId);
            return;
        }

        IssuedUserActionToken issuedUserActionToken = userActionTokenService.issueToken(
                currentUserId,
                UserActionTokenType.EMAIL_VERIFICATION,
                emailVerificationProperties.tokenTTL()
        );

        eventPublisher.publishEvent(
                new EmailVerificationRequestedEvent(currentUserId, user.email(), user.firstName(),
                        issuedUserActionToken.rawToken(), issuedUserActionToken.expiresAt(), emailVerificationProperties.verificationUrl()
                )
        );
    }

    @Transactional
    public void verifyEmail(@NonNull String rawToken) {
        long userId = userActionTokenService.consumeToken(rawToken, UserActionTokenType.EMAIL_VERIFICATION);
        userAccountService.markEmailVerified(userId);
        log.info("Email successfully verified for user id={}", userId);
    }
}
