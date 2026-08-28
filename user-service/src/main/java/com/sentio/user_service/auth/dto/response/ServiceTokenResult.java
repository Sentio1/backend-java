package com.sentio.user_service.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceTokenResult(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn
) {}
