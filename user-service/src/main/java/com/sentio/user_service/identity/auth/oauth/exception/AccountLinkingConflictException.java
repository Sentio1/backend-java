package com.sentio.user_service.identity.auth.oauth.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import org.springframework.http.HttpStatus;

public class AccountLinkingConflictException extends AppException {

    public AccountLinkingConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "CONFLICT");
    }
}
