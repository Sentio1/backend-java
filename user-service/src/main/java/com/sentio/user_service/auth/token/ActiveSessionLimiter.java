package com.sentio.user_service.auth.token;

import com.sentio.user_service.refresh_token.RefreshToken;
import com.sentio.user_service.refresh_token.RefreshTokenConstants;
import com.sentio.user_service.refresh_token.RefreshTokenRepository;
import com.sentio.user_service.refresh_token.finder.RefreshTokenFinder;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ActiveSessionLimiter {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenFinder refreshTokenFinder;

    // Caps unbounded growth of refresh_tokens per user: revoking (not deleting) the
    // oldest active sessions once the new one would push the count past the limit -
    // same "Active sessions" concept as Telegram/Google's "log out other devices",
    // just applied eagerly instead of waiting for the user to do it themselves.
    @Transactional
    public void enforceActiveSessionLimit(long userId) {
        List<RefreshToken> activeSessions =
                refreshTokenFinder.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId);

        int overLimitBy = activeSessions.size() - RefreshTokenConstants.MAX_ACTIVE_SESSIONS + 1;
        if (overLimitBy <= 0) {
            return;
        }

        Instant now = Instant.now();
        List<RefreshToken> oldest = activeSessions.subList(0, overLimitBy);
        oldest.forEach(session -> session.setRevokedAt(now));
        refreshTokenRepository.saveAll(oldest);
    }
}
