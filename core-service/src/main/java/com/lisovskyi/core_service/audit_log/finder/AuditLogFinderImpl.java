package com.lisovskyi.core_service.audit_log.finder;

import com.lisovskyi.core_service.audit_log.AuditLog;
import com.lisovskyi.core_service.audit_log.AuditLogRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogFinderImpl extends AbstractEntityFinder<AuditLog, Long> implements AuditLogFinder {

    private final AuditLogRepository auditLogRepository;

    @Override
    protected JpaRepository<AuditLog, Long> getRepository() {
        return auditLogRepository;
    }

    @Override
    protected String getEntityName() {
        return "AuditLog";
    }

    @Override
    public Page<AuditLog> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable) {
        requireNonNull(caseId, organizationId);
        return auditLogRepository.findAllByCaseIdAndOrganizationId(caseId, organizationId, pageable);
    }
}
