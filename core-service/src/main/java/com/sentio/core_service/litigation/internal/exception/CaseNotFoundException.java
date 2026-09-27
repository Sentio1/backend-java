package com.sentio.core_service.litigation.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class CaseNotFoundException extends ResourceNotFoundException {

    public CaseNotFoundException(long id) {
        super("Case", "id", id);
    }
}
