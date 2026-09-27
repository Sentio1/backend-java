package com.sentio.user_service.identity.event;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record PasswordResetRequestedEvent(
        Long userId,
        String email,
        String firstName,
        String rawToken,
        Instant expiresAt,
        String resetUrl
) {

    @Override
    public @NonNull String toString() {
        return "PasswordResetRequested[" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", rawToken=[PROTECTED]" +
                ", expiresAt=" + expiresAt +
                ", resetUrl='" + resetUrl + '\'' +
                ']';
    }
}
