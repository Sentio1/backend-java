package com.lisovskyi.core_service.audit_log.mapper;

import com.lisovskyi.core_service.audit_log.AuditLog;
import com.lisovskyi.core_service.audit_log.dto.response.AuditLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
