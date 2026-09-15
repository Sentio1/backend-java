package com.sentio.user_service.identity.user.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String fieldName, Object fieldValue) {
        super("User", fieldName, fieldValue);
    }
}
