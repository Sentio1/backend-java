package com.sentio.user_service.auth.oauth.dto;

/**
 * Claims lifted out of the Google OIDC {@code OAuth2User} that {@link
 * com.sentio.user_service.auth.oauth.GoogleAccountResolver} needs. Keeping this
 * as its own type (instead of passing four loose strings) is what let us add
 * {@code emailVerified} without another constructor-parameter shuffle.
 */
public record GoogleIdentity(
        String sub,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified
) {}
