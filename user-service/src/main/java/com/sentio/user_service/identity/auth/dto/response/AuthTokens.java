package com.sentio.user_service.identity.auth.dto.response;

public record AuthTokens(
        String accessToken,
        String refreshToken
) {}
