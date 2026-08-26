package com.lisovskyi.core_service.security;

import com.lisovskyi.security.autoconfigure.security.SecurityPrincipal;

/**
 * The authenticated caller as seen by core-service. There's no local users table to load a real
 * profile from - JwtAuthFilter only ever hands {@link CoreUserByIdDetailsService} the id out of
 * the JWT's {@code sub} claim, so that's all this principal carries. Everything else a request
 * needs (organization context, etc.) comes from the JWT's other claims via
 * {@link com.lisovskyi.security.autoconfigure.security.SecurityUtils#getCurrentClaim} /
 * {@link com.sentio.shared.security.OrganizationContext}, not from here.
 */
public record ClientPrincipal(Long id) implements SecurityPrincipal {

    @Override
    public Long getId() {
        return id;
    }

    // core-service doesn't gate anything on role - membership/org context already comes
    // from the org_id claim (see OrganizationContext). A fixed value keeps
    // SecurityPrincipal.getAuthorities() (ROLE_USER) satisfied without pretending this
    // service has an opinion on platform/org roles.
    @Override
    public String getRole() {
        return "USER";
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    // Never checked - core-service never authenticates by password, only by a JWT
    // already verified upstream by JwtAuthFilter.
    @Override
    public String getPassword() {
        return null;
    }
}
