package com.sentio.user_service.refresh_token.api.dto;

import com.sentio.user_service.refresh_token.api.enums.RevokeReason;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

public record RefreshTokenDto(
        long id,
        long userId,
        UUID familyId,
        String tokenHash,
        String userAgent,
        InetAddress ip,
        Instant expiresAt,
        Instant familyExpiresAt,
        @Nullable Instant revokedAt,
        @Nullable RevokeReason revokeReason
) {}
