package com.lisovskyi.core_service.security;

import com.lisovskyi.security.autoconfigure.security.SecurityPrincipal;
import com.lisovskyi.security.autoconfigure.security.UserByIdDetailsService;
import org.springframework.stereotype.Component;

/**
 * Without this bean, JwtAuthFilter never gets built at all (it's only created when a
 * UserByIdDetailsService is available - see DefaultSecurityAutoConfiguration in the security
 * starter), so every request to core-service falls through as unauthenticated.
 *
 * <p>No DB lookup here on purpose: core-service has no users table (separate DB per service), and
 * doesn't need one - JwtAuthFilter already verified the token's signature before calling this, so
 * the id it hands us is trustworthy as-is.
 */
@Component
public class CoreUserByIdDetailsService implements UserByIdDetailsService {

    @Override
    public SecurityPrincipal loadUserById(Long userId) {
        return new ClientPrincipal(userId);
    }
}
