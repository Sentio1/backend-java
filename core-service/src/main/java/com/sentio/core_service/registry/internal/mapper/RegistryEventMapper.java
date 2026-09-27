package com.sentio.core_service.registry.internal.mapper;

import com.sentio.core_service.litigation.api.dto.CaseEventAutoRegisterRequest;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.registry.internal.dto.RegistryDocumentFoundEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface RegistryEventMapper {

    @Mapping(target = "eventCode", source = "type")
    @Mapping(target = "registryDocumentId", source = "registryDocId")
    @Mapping(target = "title", source = "court")
    @Mapping(target = "registryDocumentTextRef", source = "documentTextRef")
    @Mapping(target = "description", ignore = true)
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
