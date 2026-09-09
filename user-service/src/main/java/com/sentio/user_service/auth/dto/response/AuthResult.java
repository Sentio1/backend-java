package com.sentio.user_service.auth.dto.response;

import com.sentio.user_service.user.dto.UserContextResponse;

/** AuthResult record. */
public record AuthResult(AuthTokens authTokens, UserContextResponse userContext) {}
