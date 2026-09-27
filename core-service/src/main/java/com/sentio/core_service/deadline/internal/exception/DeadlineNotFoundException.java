package com.sentio.core_service.deadline.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class DeadlineNotFoundException extends ResourceNotFoundException {

    public DeadlineNotFoundException(long deadlineId) {
        super("Deadline", "id", deadlineId);
    }
}
