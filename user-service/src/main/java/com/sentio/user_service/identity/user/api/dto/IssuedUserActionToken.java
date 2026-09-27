package com.sentio.user_service.identity.user.api.dto;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record IssuedUserActionToken(
        Long tokenId,
        String rawToken,
        Instant expiresAt
) {

    @Override
    public @NonNull String toString() {
        return "IssuedUserActionToken[" +
                "tokenId=" + tokenId +
                ", rawToken=[PROTECTED]" +
                ", expiresAt=" + expiresAt +
                ']';
    }
}
