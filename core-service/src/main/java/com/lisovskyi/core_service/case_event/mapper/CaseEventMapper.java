package com.lisovskyi.core_service.case_event.mapper;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventAutoRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventOccurredAtHistoryResponse;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event_occurred_at_history.CaseEventOccurredAtHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CaseEventMapper {

    @Mapping(target = "id", source = "caseEvent.id")
    @Mapping(target = "caseId", source = "caseEvent.case_.id")
    @Mapping(target = "deadlineId", source = "deadlineId")
    CaseEventResponse toResponse(CaseEvent caseEvent, Long deadlineId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "case_", source = "case_")
    CaseEvent toEntity(CaseEventManualRegisterRequest request, Long organizationId, Case case_);

    @Mapping(target = "caseEventId", source = "caseEvent.id")
    CaseEventOccurredAtHistoryResponse toResponse(CaseEventOccurredAtHistory caseEventOccurredAtHistory);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "case_", source = "case_")
    CaseEvent toEntity(CaseEventAutoRegisterRequest request, Long organizationId, Case case_);
}
