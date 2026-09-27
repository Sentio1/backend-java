package com.sentio.user_service.identity.user.internal.service;

import com.sentio.shared.hash.CryptoUtility;
import com.sentio.user_service.identity.user.api.dto.IssuedUserActionToken;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;
import com.sentio.user_service.identity.user.api.service.UserActionTokenService;
import com.sentio.user_service.identity.user.internal.model.UserActionToken;
import com.sentio.user_service.identity.user.internal.exception.InvalidUserActionTokenException;
import com.sentio.user_service.identity.user.internal.repository.UserActionTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserActionTokenServiceImpl implements UserActionTokenService {

    private final UserActionTokenRepository userActionTokenRepository;

    @Override
    @Transactional
    public IssuedUserActionToken issueToken(Long userId, UserActionTokenType tokenType, Duration ttl) {
        int invalidated = userActionTokenRepository.invalidateAllActiveTokens(userId, tokenType, Instant.now());
        if (invalidated > 0) {
            log.info("Invalidated {} active token(s) for userId={}, type={}", invalidated, userId, tokenType);
        }

        String newRawToken = CryptoUtility.generateRawToken();
        String newHashedToken = CryptoUtility.sha256(newRawToken);

        UserActionToken userActionToken = UserActionToken.create(userId, newHashedToken, tokenType, ttl);
        userActionTokenRepository.save(userActionToken);

        log.info("Issued action token id={} for userId={}, type={}", userActionToken.getId(), userId, tokenType);

        return new IssuedUserActionToken(userActionToken.getId(), newRawToken, userActionToken.getExpiresAt());
    }

    @Override
    @Transactional
    public Long consumeToken(@NonNull String rawToken, UserActionTokenType tokenType) {
        String hashedToken = CryptoUtility.sha256(rawToken);

        UserActionToken userActionToken = userActionTokenRepository.findByHashAndTokenTypeForUpdate(hashedToken, tokenType)
                .orElseThrow(() -> {
                    log.warn("Failed to consume action token: not found for type={}", tokenType);
                    return new InvalidUserActionTokenException();
                });

        userActionToken.markAsUsed();
        userActionTokenRepository.save(userActionToken);

        log.info("Consumed action token id={} for userId={}, type={}",
                userActionToken.getId(), userActionToken.getUserId(), tokenType);

        return userActionToken.getUserId();
    }

    @Override
    @Transactional
    public int invalidateActiveTokens(Long userId, UserActionTokenType tokenType) {
        int invalidated = userActionTokenRepository.invalidateAllActiveTokens(userId, tokenType, Instant.now());
        log.info("Invalidated {} active token(s) for userId={}, type={}", invalidated, userId, tokenType);
        return invalidated;
    }
}
