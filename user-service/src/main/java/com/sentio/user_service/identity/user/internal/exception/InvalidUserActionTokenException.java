package com.sentio.user_service.identity.user.internal.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import org.springframework.http.HttpStatus;

public class InvalidUserActionTokenException extends AppException {

    private static final String DEFAULT_CODE = "INVALID_USER_ACTION_TOKEN";

    public InvalidUserActionTokenException() {
        super("User action token is invalid, expired, or has already been used", HttpStatus.BAD_REQUEST, DEFAULT_CODE);
    }

    public InvalidUserActionTokenException(String message) {
        super(message, HttpStatus.BAD_REQUEST, DEFAULT_CODE);
    }

    public InvalidUserActionTokenException(String message, String code) {
        super(message, HttpStatus.BAD_REQUEST, code);
    }
}
