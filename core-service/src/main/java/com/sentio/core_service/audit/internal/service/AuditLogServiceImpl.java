package com.sentio.core_service.audit.internal.service;

import com.sentio.core_service.audit.api.dto.AuditLogResponse;
import com.sentio.core_service.audit.api.enums.ChangedByType;
import com.sentio.core_service.audit.api.enums.EntityType;
import com.sentio.core_service.audit.api.service.AuditLogService;
import com.sentio.core_service.audit.internal.model.AuditLog;
import com.sentio.core_service.audit.internal.mapper.AuditLogMapper;
import com.sentio.core_service.audit.internal.repository.AuditLogRepository;
import com.sentio.shared.entity.id.EntityId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    // Ids are sequence-generated and always positive - an IN (-1) matches nothing, and saves
    // relying on how a given Hibernate version renders an empty IN list.
    private static final List<Long> NO_IDS = List.of(-1L);

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(
            OrganizationId organizationId,
            EntityType entityType,
            EntityId entityId,
            UserId changedBy,
            String fieldName,
            String oldValue,
            String newValue
    ) {
        save(organizationId, entityType, entityId, changedBy.id(), ChangedByType.USER, fieldName, oldValue, newValue);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void logSystemChange(
            OrganizationId organizationId,
            EntityType entityType,
            EntityId entityId,
            String fieldName,
            String oldValue,
            String newValue
    ) {
        save(organizationId, entityType, entityId, null, ChangedByType.SYSTEM, fieldName, oldValue, newValue);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findCaseHistory(
            long organizationId,
            long caseId,
            Collection<Long> caseEventIds,
            Collection<Long> deadlineIds,
            Pageable pageable
    ) {
        return auditLogRepository
                .findCaseHistory(
                        organizationId,
                        caseId,
                        caseEventIds.isEmpty() ? NO_IDS : caseEventIds,
                        deadlineIds.isEmpty() ? NO_IDS : deadlineIds,
                        pageable)
                .map(auditLogMapper::toResponse);
    }

    private void save(
            OrganizationId organizationId,
            EntityType entityType,
            EntityId entityId,
            Long changedBy,
            ChangedByType changedByType,
            String fieldName,
            String oldValue,
            String newValue
    ) {
        AuditLog auditLog = AuditLog.builder()
                .organizationId(organizationId.id())
                .entityType(entityType)
                .entityId(entityId.id())
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .changedByType(changedByType)
                .changedAt(Instant.now())
                .build();

        auditLogRepository.save(auditLog);
    }
}
