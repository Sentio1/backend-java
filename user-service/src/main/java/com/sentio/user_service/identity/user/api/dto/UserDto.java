package com.sentio.user_service.identity.user.api.dto;

import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record UserDto(
        long id,
        String email,
        @Nullable String password,
        PlatformRole platformRole,
        @Nullable Instant emailVerifiedAt,
        boolean deleted
) {
    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }
}
