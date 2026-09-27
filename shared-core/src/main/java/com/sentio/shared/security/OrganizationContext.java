package com.sentio.shared.security;

import com.lisovskyi.security.autoconfigure.security.SecurityUtils;
import java.util.Optional;

/**
 * Reads the {@code org_id} claim that {@code TokenIssuer} (user-service) bakes into the JWT at
 * login/switch-org time. Sentio-specific on top of the generic
 * {@link SecurityUtils#getCurrentClaim(String)} - the claim name and its numeric shape belong to
 * this app's token contract, not to the reusable security-starter, so this is the one place that
 * hardcodes {@code "org_id"}.
 *
 * <p>Every service on a shared JWT (core-service included, which has no local membership table to
 * re-verify against) reads the caller's organization through here rather than trusting a
 * client-supplied header/path segment.
 */
public final class OrganizationContext {

    private OrganizationContext() {
        throw new UnsupportedOperationException();
    }

    private static final String ORG_ID_CLAIM = "org_id";

    /**
     * Numeric because JSON claims round-trip through jjwt as {@code Integer}/{@code Long}
     * depending on magnitude - never assume the concrete boxed type.
     */
    public static Optional<Long> getCurrentOrganizationId() {
        return SecurityUtils.getCurrentClaim(ORG_ID_CLAIM)
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue());
    }
}
