package com.sentio.core_service.deadline.internal.exception;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;

public class DeadlineRuleNotFoundException extends ResourceNotFoundException {

    public DeadlineRuleNotFoundException(long deadlineRuleId) {
        super("DeadlineRule", "id", deadlineRuleId);
    }
}
