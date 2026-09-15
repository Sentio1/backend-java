package com.sentio.user_service.identity.user.api.dto;

import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import org.jspecify.annotations.Nullable;

public record UserDto(
        long id,
        String email,
        @Nullable String password,
        PlatformRole platformRole
) {}
