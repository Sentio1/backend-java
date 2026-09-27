package com.sentio.user_service.identity.event;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record EmailVerificationRequestedEvent(
        Long userId,
        String email,
        String firstName,
        String rawToken,
        Instant expiresAt,
        String verificationUrl
) {

    @Override
    public @NonNull String toString() {
        return "EmailVerificationRequestedEvent[" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", rawToken=[PROTECTED]" +
                ", expiresAt=" + expiresAt +
                ", verificationUrl='" + verificationUrl + '\'' +
                ']';
    }
}
