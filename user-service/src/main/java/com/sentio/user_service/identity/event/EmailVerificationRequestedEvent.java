package com.sentio.user_service.identity.event;

import lombok.Builder;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneId;

@Builder
public record EmailVerificationRequestedEvent(
        Long userId,
        String email,
        String firstName,
        String rawToken,
        Instant expiresAt,
        String verificationUrl,
        ZoneId userZone
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
                ", userZone='" + userZone + '\'' +
                ']';
    }
}
