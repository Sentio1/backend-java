package com.sentio.user_service.identity.user.internal.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import org.springframework.http.HttpStatus;

/** Restore was requested for a user that isn't soft-deleted. */
public class UserNotDeletedException extends AppException {

    public UserNotDeletedException(long userId) {
        super("User " + userId + " is not deleted", HttpStatus.CONFLICT, "USER_NOT_DELETED");
    }
}
