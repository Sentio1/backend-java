package com.sentio.user_service.refresh_token.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class RefreshTokenNotFoundException extends ResourceNotFoundException {

    public RefreshTokenNotFoundException(String message) {
        super(message);
    }

    public RefreshTokenNotFoundException(String fieldName, Object fieldValue) {
        super("RefreshToken", fieldName, fieldValue);
    }
}
