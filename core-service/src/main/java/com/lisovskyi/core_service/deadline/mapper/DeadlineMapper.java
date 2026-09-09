package com.lisovskyi.core_service.deadline.mapper;

import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import com.lisovskyi.core_service.deadline_processor.ExplanationBuilder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DeadlineMapper {

    @Mapping(target = "caseId", source = "case_.id")
    @Mapping(target = "triggeringEventId", source = "triggeringEvent.id")
    @Mapping(target = "ruleId", source = "rule.id")
    @Mapping(target = "explanation", expression = "java(explanation(deadline))")
    DeadlineResponse toResponse(Deadline deadline);

    // Знімок (durationValue/durationUnit/dayKind/baseDate/naiveDueOn) є лише в дедлайнів,
    // порахованих після SEN-28 - для старих рядків він null, і пояснення тоді теж null,
    // а не текст на основі "живого" DeadlineRule (втратив би сенс знімку).
    default String explanation(Deadline deadline) {
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
                deadline.getTriggeringEvent() != null ? deadline.getTriggeringEvent().getEventCode() : null);
    }
}
