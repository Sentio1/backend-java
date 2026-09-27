package com.sentio.user_service.identity.event;

import lombok.Builder;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneId;

@Builder
public record PasswordResetRequestedEvent(
        Long userId,
        String email,
        String firstName,
        String rawToken,
        Instant expiresAt,
        String resetUrl,
        ZoneId userZone
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
                ", userZone='" + userZone + '\'' +
                ']';
    }
}
