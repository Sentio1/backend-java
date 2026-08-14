package com.sentio.user_service.auth.dto;

public record AuthResult(
        AuthTokens authTokens,
        UserContextResponse userContext
) {}
