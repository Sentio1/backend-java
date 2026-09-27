package com.sentio.user_service.identity.event;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record EmailVerificationRequested(
        Long userId,
        String email,
        String firstName,
        String rawToken,
        Instant expiresAt,
        String verificationUrl
) {

    @Override
    public @NonNull String toString() {
        return "EmailVerificationRequested[" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", rawToken=[PROTECTED]" +
                ", expiresAt=" + expiresAt +
                ", verificationUrl='" + verificationUrl + '\'' +
                ']';
    }
}
