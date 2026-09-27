package com.sentio.user_service.identity.user.internal.service;

import com.sentio.user_service.identity.user.internal.repository.UserActionTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserActionTokenCleanupJob {

    private static final int RETENTION_DAYS = 30;

    private final UserActionTokenRepository userActionTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupJob() {
        Instant retentionCutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        int deletedExpired = userActionTokenRepository.deleteAllByExpiresAtBefore(retentionCutoff);

        log.info("Deleted {} expired tokens", deletedExpired);
    }
}
