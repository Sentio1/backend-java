package com.sentio.user_service.identity.auth.dto.response;

import com.sentio.user_service.identity.user.api.dto.UserContextResponse;

public record AuthResult(
        AuthTokens authTokens,
        UserContextResponse userContext
) {}
