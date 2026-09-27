package com.sentio.core_service.deadline.internal.exception;

import com.lisovskyi.web.error.autoconfigure.base.AppException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * A new version of a rule must start strictly after the current one - otherwise the two validity
 * ranges would overlap. 409: conflicts with the rule's existing version, not a malformed request.
 */
public class DeadlineRuleValidFromConflictException extends AppException {

    public DeadlineRuleValidFromConflictException(String code, LocalDate previousValidFrom, LocalDate requestedValidFrom) {
        super("New version of rule " + code + " must be valid from after " + previousValidFrom
                        + " (the current version's validFrom), got " + requestedValidFrom,
                HttpStatus.CONFLICT, "DEADLINE_RULE_VALID_FROM_CONFLICT");
    }
}
