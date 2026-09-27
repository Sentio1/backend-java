package com.sentio.core_service.audit.internal.mapper;

import com.sentio.core_service.audit.internal.model.AuditLog;
import com.sentio.core_service.audit.api.dto.AuditLogResponse;
import org.mapstruct.Mapper;

@Mapper
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
