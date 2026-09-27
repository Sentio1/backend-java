package com.sentio.user_service.identity.auth.exception;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;

/**
 * A rotated refresh token was presented again after the grace period - treated as theft, the
 * whole family gets revoked. Its own type (rather than a plain UnauthorizedException) so that
 * AuthService.refresh can exclude exactly this one from rollback: the family revocation has to be
 * committed even though the request itself fails with 401.
 */
public class RefreshTokenReusedException extends UnauthorizedException {

    public RefreshTokenReusedException(String message) {
        super(message);
    }
}
