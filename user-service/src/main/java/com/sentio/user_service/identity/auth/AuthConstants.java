package com.sentio.user_service.identity.auth;

public final class AuthConstants {

    private AuthConstants() {
        throw new UnsupportedOperationException();
    }

    public static final int PASSWORD_MIN_LENGTH = 10;
    public static final int PASSWORD_MAX_LENGTH = 72;
}
