package com.sentio.user_service.refresh_token.api.dto;

import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.time.Instant;

public record RefreshTokenDto(
        long id,
        long userId,
        String tokenHash,
        String userAgent,
        InetAddress ip,
        Instant expiresAt,
        @Nullable Instant revokedAt
) {}
