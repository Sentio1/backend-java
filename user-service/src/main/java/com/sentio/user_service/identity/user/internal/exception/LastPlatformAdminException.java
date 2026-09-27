package com.sentio.user_service.identity.user.internal.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import org.springframework.http.HttpStatus;

/** Demoting this user would leave the platform without any ADMIN. */
public class LastPlatformAdminException extends AppException {

    public LastPlatformAdminException(long userId) {
        super("Cannot demote user " + userId + ": they are the last platform admin",
                HttpStatus.CONFLICT, "LAST_PLATFORM_ADMIN");
    }
}
