package com.sentio.user_service.identity.organization.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class OrganizationMemberNotFoundException extends ResourceNotFoundException {

    public OrganizationMemberNotFoundException(String message) {
        super(message);
    }

    public OrganizationMemberNotFoundException(String fieldName, Object fieldValue) {
        super("OrganizationMember", fieldName, fieldValue);
    }
}
