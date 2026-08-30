package com.lisovskyi.core_service.deadline.mapper;

import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DeadlineMapper {

    @Mapping(target = "caseId", source = "case_.id")
    @Mapping(target = "triggeringEventId", source = "triggeringEvent.id")
    @Mapping(target = "ruleId", source = "rule.id")
    DeadlineResponse toResponse(Deadline deadline);
}
