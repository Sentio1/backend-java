package com.sentio.user_service.identity.auth;

import java.time.Duration;

public final class AuthConstants {

    private AuthConstants() {
        throw new UnsupportedOperationException();
    }

    public static final int PASSWORD_MIN_LENGTH = 10;
    public static final int PASSWORD_MAX_LENGTH = 72;

    // BCrypt only ever looks at the first 72 *bytes* of a password - and a Cyrillic
    // letter is 2 bytes in UTF-8, so PASSWORD_MAX_LENGTH (chars) alone doesn't cover it.
    public static final int PASSWORD_MAX_BYTES = 72;

    // Two tabs refreshing with the same cookie at the same moment is normal, not
    // theft: within this window after a rotation, presenting the rotated token again
    // is just rejected; after it, it counts as reuse and kills the whole family.
    public static final Duration REFRESH_REUSE_GRACE_PERIOD = Duration.ofSeconds(10);

    public static final String INVALID_CREDENTIALS_MSG = "Invalid email or password";
    public static final String INVALID_REFRESH_TOKEN_MSG = "Invalid refresh token";
}
