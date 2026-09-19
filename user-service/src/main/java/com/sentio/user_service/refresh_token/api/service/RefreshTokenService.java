package com.sentio.user_service.refresh_token.api.service;

import com.sentio.user_service.refresh_token.api.dto.RefreshTokenDto;
import com.sentio.user_service.refresh_token.api.dto.SessionResponse;
import com.sentio.user_service.refresh_token.api.enums.RevokeReason;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {

    // A brand new session (login/register/Google): new family, whose absolute
    // lifetime is capped at familyExpiresAt no matter how often it gets rotated.
    RefreshTokenDto startSession(
            long userId, String tokenHash, String userAgent, InetAddress ip,
            Instant expiresAt, Instant familyExpiresAt);

    // Revokes `current` as ROTATED and issues its successor in the same family.
    // expiresAt is clamped to the family's absolute limit.
    RefreshTokenDto rotate(
            RefreshTokenDto current, String newTokenHash, String userAgent, InetAddress ip, Instant expiresAt);

    // Locks the row (SELECT ... FOR UPDATE) for the rest of the caller's transaction,
    // so two concurrent refreshes of the same token are serialized.
    Optional<RefreshTokenDto> findByTokenHash(String tokenHash);

    List<SessionResponse> findActiveSessions(long userId);

    void revokeSession(long userId, long refreshTokenId);

    void revokeSessions(List<Long> refreshTokenIds);

    void revokeAllActiveForUser(long userId);

    void revokeFamily(UUID familyId, RevokeReason reason);

    // Caps unbounded growth of refresh_tokens per user: revokes (not deletes) the
    // oldest active sessions once issuing a new one would push the count past the
    // configured limit - same "Active sessions" concept as Telegram/Google's "log out
    // other devices", just applied eagerly instead of waiting for the user to do it.
    void enforceActiveSessionLimit(long userId);
}
