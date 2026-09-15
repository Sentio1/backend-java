package com.sentio.user_service.refresh_token.api.service;

import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.dto.SessionResponse;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenService {

    RefreshTokenDto issue(long userId, String tokenHash, String userAgent, InetAddress ip, Instant expiresAt);

    Optional<RefreshTokenDto> findByTokenHash(String tokenHash);

    List<SessionResponse> findActiveSessions(long userId);

    void revokeSession(long userId, long refreshTokenId);

    void revokeSessions(List<Long> refreshTokenIds);

    void revokeAllActiveForUser(long userId);

    // Caps unbounded growth of refresh_tokens per user: revokes (not deletes) the
    // oldest active sessions once issuing a new one would push the count past the
    // configured limit - same "Active sessions" concept as Telegram/Google's "log out
    // other devices", just applied eagerly instead of waiting for the user to do it.
    void enforceActiveSessionLimit(long userId);
}
