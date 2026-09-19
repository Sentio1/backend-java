package com.sentio.user_service.identity.user.api.dto;

import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import org.jspecify.annotations.Nullable;

// A user created from an external identity provider that has already verified
// the email (callers must check that before creating) - so the account is
// created with email_verified_at set.
public record NewExternalUser(
        AuthProvider provider,
        String providerUserId,
        String email,
        @Nullable String firstName,
        @Nullable String lastName
) {}
