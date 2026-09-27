package com.sentio.user_service.identity.user.api.service;

import com.sentio.user_service.identity.user.api.dto.IssuedUserActionToken;
import com.sentio.user_service.identity.user.api.enums.UserActionTokenType;

import java.time.Duration;

public interface UserActionTokenService {

    IssuedUserActionToken issueToken(Long userId, UserActionTokenType tokenType, Duration ttl);

    Long consumeToken(String rawToken, UserActionTokenType tokenType);

    int invalidateActiveTokens(Long userId, UserActionTokenType tokenType);
}
