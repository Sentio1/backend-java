package com.lisovskyi.core_service.registry_event.mapper;

import com.lisovskyi.core_service.case_event.dto.request.CaseEventAutoRegisterRequest;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.registry_event.dto.RegistryDocumentFoundEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RegistryEventMapper {

    @Mapping(target = "eventCode", source = "type")
    @Mapping(target = "registryDocumentId", source = "registryDocId")
    @Mapping(target = "title", source = "court")
    @Mapping(target = "registryDocumentTextRef", source = "documentTextRef")
    CaseEventAutoRegisterRequest toRegisterRequest(RegistryDocumentFoundEvent registryDocumentFoundEvent);

    default EventCode mapDocumentType(String type) {
        return switch (type) {
            case "CLAIM" -> EventCode.CLAIM_FILED;
            case "DECISION" -> EventCode.DECISION;
            case "RULING" -> EventCode.RULING_RECEIVED;
            case "COPY" -> EventCode.COPY_SERVED;
            case "HEARING" -> EventCode.HEARING;
            default -> EventCode.OTHER;
        };
    }
}
