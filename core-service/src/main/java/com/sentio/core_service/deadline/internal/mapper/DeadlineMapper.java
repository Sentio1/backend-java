package com.sentio.core_service.deadline.internal.mapper;

import com.sentio.core_service.deadline.internal.model.Deadline;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineResponse;
import com.sentio.core_service.deadline.internal.engine.ExplanationBuilder;
import com.sentio.core_service.litigation.api.enums.EventCode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface DeadlineMapper {

    // triggerEventCode - the triggering event's code, looked up by the caller in litigation (Deadline
    // only stores triggeringEventId); null for a manual deadline without an event.
    @Mapping(target = "ruleId", source = "deadline.rule.id")
    @Mapping(target = "explanation", expression = "java(explanation(deadline, triggerEventCode))")
    DeadlineResponse toResponse(Deadline deadline, EventCode triggerEventCode);

    // Знімок (durationValue/durationUnit/dayKind/baseDate/naiveDueOn) є лише в дедлайнів,
    // порахованих після SEN-28 - для старих рядків він null, і пояснення тоді теж null,
    // а не текст на основі "живого" DeadlineRule (втратив би сенс знімку).
    default String explanation(Deadline deadline, EventCode triggerEventCode) {
        if (deadline.getDurationValue() == null
                || deadline.getDurationUnit() == null
                || deadline.getDayKind() == null
                || deadline.getBaseDate() == null
                || deadline.getNaiveDueOn() == null) {
            return null;
        }
        return ExplanationBuilder.build(
                deadline.getDurationValue(),
                deadline.getDurationUnit(),
                deadline.getDayKind(),
                deadline.getBaseDate(),
                deadline.getLegalBasis(),
                deadline.getNaiveDueOn(),
                deadline.getDueOn(),
                triggerEventCode);
    }
}
