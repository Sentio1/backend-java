package com.sentio.user_service.refresh_token;

/** RefreshTokenConstants class. */
public final class RefreshTokenConstants {

    private RefreshTokenConstants() {
        throw new UnsupportedOperationException();
    }

    public static final int TOKEN_MAX_LENGTH = 64;

    // Oldest active sessions beyond this get revoked when a new one is issued -
    // caps unbounded growth without needing a separate cleanup job.
    public static final int MAX_ACTIVE_SESSIONS = 5;
}
