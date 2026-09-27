package com.sentio.user_service.refresh_token.api.enums;

// Mirrors auth.refresh_token_revoke_reason (V10). Only ROTATED means "replaced by
// a newer token of the same family" - that's the one state where presenting the
// token again is reuse (theft signal) rather than a plain stale/expired session.
public enum RevokeReason {
    ROTATED,
    LOGOUT,
    SESSION_LIMIT,
    REUSE_DETECTED,
    ALL_SESSIONS_REVOKED
}
