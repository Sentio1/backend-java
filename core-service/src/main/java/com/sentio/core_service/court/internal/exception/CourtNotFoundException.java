package com.sentio.core_service.court.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class CourtNotFoundException extends ResourceNotFoundException {

    public CourtNotFoundException(long courtId) {
        super("Court", "id", courtId);
    }
}
