package com.sentio.core_service.litigation.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class CaseEventNotFoundException extends ResourceNotFoundException {

    public CaseEventNotFoundException(long id) {
        super("CaseEvent", "id", id);
    }
}
