package com.lisovskyi.core_service.audit_log.finder;

import com.lisovskyi.core_service.audit_log.AuditLog;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogFinder extends EntityFinder<AuditLog, Long> {

    Page<AuditLog> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable);
}
