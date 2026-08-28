package com.lisovskyi.core_service.case_event.mapper;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventAutoRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CaseEventMapper {

    @Mapping(target = "id", source = "caseEvent.id")
    @Mapping(target = "caseId", source = "case_.id")
    CaseEventResponse toResponse(CaseEvent caseEvent);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "case_", source = "case_")
    CaseEvent toEntity(CaseEventManualRegisterRequest request, Long organizationId, Case case_);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "case_", source = "case_")
    CaseEvent toEntity(CaseEventAutoRegisterRequest request, Long organizationId, Case case_);
}
