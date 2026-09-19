package com.sentio.user_service.identity.auth.service;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Checks a raw password against a stored hash in constant-ish time regardless of whether the
 * account exists or has a password at all. Without the dummy comparison, "no such user" returns
 * immediately while a real account costs a full BCrypt round (~250 ms at strength 12) - which
 * tells an attacker exactly which emails are registered.
 */
@Component
public class PasswordVerifier {

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public PasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** False for a null/blank stored hash (OAuth-only or unprovisioned account) - never "skip the check". */
    public boolean matches(String rawPassword, @Nullable String storedHash) {
        if (!StringUtils.hasText(storedHash)) {
            passwordEncoder.matches(rawPassword, dummyHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }
}
