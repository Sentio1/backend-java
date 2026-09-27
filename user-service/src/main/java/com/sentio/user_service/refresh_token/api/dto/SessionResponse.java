package com.sentio.user_service.refresh_token.api.dto;

import java.net.InetAddress;
import java.time.Instant;

public record SessionResponse(
        long id,
        String userAgent,
        InetAddress ip,
        Instant createdAt,
        Instant expiresAt,
        boolean isCurrent
) {}
