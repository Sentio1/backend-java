package com.sentio.core_service.litigation.api.dto;

import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.litigation.api.enums.ProcedureType;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Everything the deadline engine needs to know about a case event (and its case) to compute the
 * deadlines it triggers - a snapshot, not a live entity, so the deadline module never touches
 * litigation's entities. courtId is null while the case has no court yet (no deadline can be
 * computed then: court decides both the rule instance and the time zone).
 */
public record TriggeringEvent(
        long id,
        long caseId,
        long organizationId,
        EventCode eventCode,
        Instant occurredAt,
        ProcedureType procedure,
        @Nullable Long courtId
) {}
