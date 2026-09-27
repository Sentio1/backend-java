package com.sentio.user_service.identity.organization.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class OrganizationNotFoundException extends ResourceNotFoundException {

    public OrganizationNotFoundException(String message) {
        super(message);
    }

    public OrganizationNotFoundException(String fieldName, Object fieldValue) {
        super("Organization", fieldName, fieldValue);
    }
}
