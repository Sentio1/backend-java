package com.lisovskyi.core_service.deadline.exception;

import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
import com.lisovskyi.web.error.autoconfigure.base.AppException;
import com.sentio.shared.entity.id.deadline.DeadlineId;
import org.springframework.http.HttpStatus;

// Відхилити (SEN-29 AC4) можна лише строк, що ще чекає рішення - вже DONE/MISSED/EXTENDED/
// SUSPENDED, чи вже раз відхилений REJECTED, не мають ставати "ще раз відхиленими" (перезаписало
// б rejectedAt/rejectionReason і сплутало б історію, замість того щоб лишити перший запис чесним).
public class DeadlineNotRejectableException extends AppException {

    public DeadlineNotRejectableException(DeadlineId deadlineId, DeadlineStatus currentStatus) {
        super(
                "Deadline " + deadlineId + " cannot be rejected: current status is " + currentStatus,
                HttpStatus.CONFLICT,
                "DEADLINE_NOT_REJECTABLE");
    }
}
