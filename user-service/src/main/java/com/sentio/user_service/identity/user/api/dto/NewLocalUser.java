package com.sentio.user_service.identity.user.api.dto;

import org.jspecify.annotations.Nullable;

// passwordHash is already encoded - hashing policy belongs to auth, the user
// module only stores it.
public record NewLocalUser(
        String email,
        String passwordHash,
        @Nullable String phoneNumber,
        String firstName,
        String lastName,
        @Nullable String middleName
) {}
