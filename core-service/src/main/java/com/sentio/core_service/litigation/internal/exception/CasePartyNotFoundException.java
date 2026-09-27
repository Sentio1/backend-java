package com.sentio.core_service.litigation.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class CasePartyNotFoundException extends ResourceNotFoundException {

    public CasePartyNotFoundException(long id) {
        super("CaseParty", "id", id);
    }
}
