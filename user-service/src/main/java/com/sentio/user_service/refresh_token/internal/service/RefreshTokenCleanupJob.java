package com.sentio.user_service.refresh_token.internal.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.sentio.user_service.refresh_token.internal.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private static final int RETENTION_DAYS = 30;

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupStaleTokens() {
        Instant retentionCutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        int deletedRevoked = refreshTokenRepository.deleteAllByRevokedAtIsNotNullAndRevokedAtBefore(retentionCutoff);
        int deletedExpired = refreshTokenRepository.deleteAllByExpiresAtBefore(Instant.now());

        log.info("Refresh token cleanup: removed {} revoked, {} expired rows", deletedRevoked, deletedExpired);
    }
}
