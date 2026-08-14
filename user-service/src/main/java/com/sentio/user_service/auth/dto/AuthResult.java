package com.sentio.user_service.auth.dto;

import com.sentio.user_service.user.dto.UserContextResponse;

public record AuthResult(
        AuthTokens authTokens,
        UserContextResponse userContext
) {}
