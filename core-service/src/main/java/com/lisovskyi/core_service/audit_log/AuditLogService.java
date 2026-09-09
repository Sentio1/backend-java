package com.lisovskyi.core_service.audit_log;

import com.lisovskyi.core_service.audit_log.dto.response.AuditLogResponse;
import com.lisovskyi.core_service.audit_log.enums.ChangedByType;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import com.lisovskyi.core_service.audit_log.finder.AuditLogFinder;
import com.lisovskyi.core_service.audit_log.mapper.AuditLogMapper;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.EntityId;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogFinder auditLogFinder;
    private final AuditLogMapper auditLogMapper;
    private final CaseFinder caseFinder;

    // Propagation.MANDATORY, а не звичайний @Transactional (яка мовчки відкриє власну нову
    // транзакцію, якщо викликана поза існуючою): AC SEN-23 вимагає, щоб збій запису аудиту
    // відкочував саму бізнес-зміну, а не навпаки. Це працює тільки якщо запис аудиту робиться
    // В ТІЙ САМІЙ транзакції, що й save() сутності, яку він описує - MANDATORY гарантує це
    // на рівні виклику: якщо колись хтось викличе log(...) поза @Transactional-методом
    // (наприклад, з @Async-обробника), застосунок впаде одразу з IllegalTransactionStateException
    // замість того, щоб мовчки завести audit-запис в ізольованій транзакції, з якої проблема
    // ніколи не відкотить основну зміну.
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(
            OrganizationId organizationId,
            EntityType entityType,
            EntityId entityId,
            UserId changedBy,
            String fieldName,
            String oldValue,
            String newValue) {
        save(organizationId, entityType, entityId, changedBy.id(), ChangedByType.USER, fieldName, oldValue, newValue);
    }

    // Той самий журнал, коли зміну спричинив автоматичний процес, а не людина (напр.
    // DeadlineEngine при перерахунку dueOn через зміну виробничого календаря) - changedBy = null
    // замість вигаданого auth.users.id, і ChangedByType.SYSTEM каже, чому саме null (див.
    // ChangedByType).
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

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getCaseAuditLog(CaseId caseId, OrganizationId organizationId, Pageable pageable) {
        // Підтверджує, що справа є (і в цій організації) перед видачею історії - інакше чужа
        // справа мовчки повертала б порожню сторінку замість 404, як і решта read-ендпоінтів
        // цього стилю (див. CaseEventOccurredAtHistoryService).
        caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        return PageResponse.of(auditLogFinder
                .findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id(), pageable)
                .map(auditLogMapper::toResponse));
    }
}
