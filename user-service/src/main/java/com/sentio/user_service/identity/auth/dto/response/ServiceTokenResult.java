package com.sentio.user_service.identity.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceTokenResult(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn
) {
    public ServiceTokenResult(String accessToken, long expiresIn) {
        this(accessToken, "Bearer", expiresIn);
    }
}
