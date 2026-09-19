package com.sentio.core_service.litigation.internal.service;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.core_service.audit.api.enums.EntityType;
import com.sentio.core_service.audit.api.service.AuditLogService;
import com.sentio.core_service.common.model.SoftDeleteManager;
import com.sentio.core_service.litigation.api.dto.CaseEventAutoRegisterRequest;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.litigation.api.service.CaseEventService;
import com.sentio.core_service.litigation.api.spi.CaseEventDeadline;
import com.sentio.core_service.litigation.api.spi.CaseEventDeadlines;
import com.sentio.core_service.litigation.internal.controller.dto.CaseEventManualRegisterRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CaseEventOccurredAtChangeRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CaseEventResponse;
import com.sentio.core_service.litigation.internal.controller.dto.CaseEventUpdateRequest;
import com.sentio.core_service.litigation.internal.model.Case;
import com.sentio.core_service.litigation.internal.model.CaseEvent;
import com.sentio.core_service.litigation.internal.model.CaseEventOccurredAtHistory;
import com.sentio.core_service.litigation.internal.enums.Source;
import com.sentio.core_service.litigation.internal.exception.CaseEventNotFoundException;
import com.sentio.core_service.litigation.internal.exception.CaseNotFoundException;
import com.sentio.core_service.litigation.internal.mapper.CaseEventMapper;
import com.sentio.core_service.litigation.internal.repository.CaseEventOccurredAtHistoryRepository;
import com.sentio.core_service.litigation.internal.repository.CaseEventRepository;
import com.sentio.core_service.litigation.internal.repository.CaseRepository;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseEventServiceImpl implements CaseEventService {

    private final CaseEventRepository caseEventRepository;
    private final CaseRepository caseRepository;
    private final CaseEventOccurredAtHistoryRepository caseEventOccurredAtHistoryRepository;

    private final CaseEventMapper caseEventMapper;
    private final CaseEventDeadlines caseEventDeadlines;
    private final AuditLogService auditLogService;

    private final SoftDeleteManager softDeleteManager;

    @Transactional(readOnly = true)
    public PageResponse<CaseEventResponse> getAllCaseEvents(
            CaseId caseId, OrganizationId organizationId, EventCode eventCode, Pageable pageable) {
        log.debug("Fetching case events for caseId: {}, orgId: {}, eventCode: {}", caseId, organizationId, eventCode);
        findCase(caseId, organizationId);

        Page<CaseEvent> caseEvents = caseEventRepository.findAllByCaseIdAndOrganizationId(
                caseId.id(), organizationId.id(), eventCode, pageable);
        List<Long> caseEventIds = caseEvents.map(CaseEvent::getId).toList();

        Map<Long, List<CaseEventDeadline>> deadlinesByCaseEventId = caseEventDeadlines.findByCaseEventIds(caseEventIds);

        return PageResponse.of(caseEvents.map(caseEvent -> caseEventMapper.toResponse(
                caseEvent, deadlinesByCaseEventId.getOrDefault(caseEvent.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public CaseEventResponse getCaseEventById(CaseId caseId, CaseEventId eventId, OrganizationId organizationId) {
        log.debug("Fetching case event id: {} for caseId: {}, orgId: {}", eventId, caseId, organizationId);
        CaseEvent caseEvent = findCaseEvent(eventId, caseId, organizationId);
        return caseEventMapper.toResponse(caseEvent, findDeadlines(caseEvent));
    }

    // Самостійно юрист створює case event
    @Transactional
    public CaseEventResponse registerCaseEvent(
            CaseId caseId, OrganizationId organizationId, UserId createdBy, CaseEventManualRegisterRequest request) {
        Case case_ = findCase(caseId, organizationId);

        CaseEvent caseEvent = caseEventMapper.toEntity(request, organizationId.id(), case_);
        caseEvent.setCreatedBy(createdBy.id());
        caseEvent.setSource(Source.MANUAL);
        caseEvent.setRegisteredAt(Instant.now());

        CaseEvent savedCaseEvent = caseEventRepository.saveAndFlush(caseEvent);
        List<CaseEventDeadline> deadlines = regenerateDeadlines(savedCaseEvent, createdBy.id());
        log.info("Manually registered case event id: {} for caseId: {}", savedCaseEvent.getId(), caseId);
        return caseEventMapper.toResponse(savedCaseEvent, deadlines);
    }

    // Приходить з Go сервісу
    @Transactional
    public CaseEventResponse registerRegistryCaseEvent(
            CaseId caseId, OrganizationId organizationId, CaseEventAutoRegisterRequest request) {
        log.debug("Auto-registering registry event for caseId: {}", caseId);
        Case case_ = findCase(caseId, organizationId);

        CaseEvent caseEvent = caseEventMapper.toEntity(request, organizationId.id(), case_);
        caseEvent.setSource(Source.REGISTRY);
        caseEvent.setRegistryDocumentId(request.registryDocumentId());
        caseEvent.setRegisteredAt(Instant.now());

        try {
            CaseEvent savedCaseEvent = caseEventRepository.saveAndFlush(caseEvent);
            // changedBy = null: автоматична реєстрація з Go-сервісу, немає людини-автора
            // (SERVICE-роль з власною ідентичністю ще не реалізована - README/SEN-33), тож зміна
            // dueOn (якщо була) потрапить в audit_log з ChangedByType.SYSTEM, а не з вигаданим
            // системним юзером.
            List<CaseEventDeadline> deadlines = regenerateDeadlines(savedCaseEvent, null);
            log.info("Successfully registered registry event id: {} for caseId: {}", savedCaseEvent.getId(), caseId);
            return caseEventMapper.toResponse(savedCaseEvent, deadlines);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, "uq_case_events_case_registry_document")) {
                log.warn(
                        "Auto-register failed: duplicate registry document {} for caseId {}",
                        request.registryDocumentId(),
                        caseId);
                throw new ResourceAlreadyExistsException(
                        "Case event with the same registryDocumentId already exists for this case");
            }
            log.error("Data integrity violation while auto-registering event for caseId {}", caseId, e);
            throw e;
        }
    }

    @Transactional
    public CaseEventResponse changeOccurredAt(
            CaseId caseId,
            CaseEventId eventId,
            OrganizationId organizationId,
            UserId changedById,
            CaseEventOccurredAtChangeRequest request) {
        log.debug("Changing occurredAt for eventId: {}, caseId: {}", eventId, caseId);
        CaseEvent caseEvent = findCaseEvent(eventId, caseId, organizationId);

        Instant oldOccurredAt = caseEvent.getOccurredAt();
        if (oldOccurredAt.equals(request.newOccurredAt())) {
            log.info("changeOccurredAt called with unchanged date for event id={}, skipping", caseEvent.getId());
            return caseEventMapper.toResponse(caseEvent, findDeadlines(caseEvent));
        }

        caseEvent.setOccurredAt(request.newOccurredAt());

        CaseEventOccurredAtHistory caseEventOccurredAtHistory = CaseEventOccurredAtHistory.builder()
                .caseEvent(caseEvent)
                .organizationId(organizationId.id())
                .oldOccurredAt(oldOccurredAt)
                .newOccurredAt(request.newOccurredAt())
                .changedBy(changedById.id())
                .changedAt(Instant.now())
                .reason(request.reason())
                .build();

        CaseEvent savedCaseEvent = caseEventRepository.save(caseEvent);
        caseEventOccurredAtHistoryRepository.save(caseEventOccurredAtHistory);

        // Окремо від caseEventOccurredAtHistory (детальніша - з причиною): SEN-23 AC вимагає
        // запису в загальний audit_log на КОЖНУ зміну case_events-поля, а occurredAt - саме те
        // поле, заради якого й писався сам тікет ("клієнт каже, що адвокат пропустив строк").
        auditLogService.log(
                organizationId,
                EntityType.CASE_EVENT,
                eventId,
                changedById,
                "occurredAt",
                oldOccurredAt.toString(),
                request.newOccurredAt().toString());

        List<CaseEventDeadline> deadlines = regenerateDeadlines(savedCaseEvent, changedById.id());
        log.info(
                "Changed occurredAt for eventId: {} (old: {}, new: {})",
                eventId,
                oldOccurredAt,
                request.newOccurredAt());
        return caseEventMapper.toResponse(savedCaseEvent, deadlines);
    }

    @Transactional
    public CaseEventResponse updateCaseEvent(
            CaseId caseId,
            CaseEventId eventId,
            OrganizationId organizationId,
            UserId changedById,
            CaseEventUpdateRequest request) {
        log.debug("Updating case eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = findCaseEvent(eventId, caseId, organizationId);

        if (request.title().isPresent()) {
            String oldTitle = caseEvent.getTitle();
            String newTitle = request.title().get();
            caseEvent.setTitle(newTitle);
            if (!Objects.equals(oldTitle, newTitle)) {
                auditLogService.log(organizationId, EntityType.CASE_EVENT, eventId, changedById, "title", oldTitle, newTitle);
            }
        }

        if (request.description().isPresent()) {
            String oldDescription = caseEvent.getDescription();
            String newDescription = request.description().get();
            caseEvent.setDescription(newDescription);
            if (!Objects.equals(oldDescription, newDescription)) {
                auditLogService.log(
                        organizationId, EntityType.CASE_EVENT, eventId, changedById, "description", oldDescription, newDescription);
            }
        }

        CaseEvent savedCaseEvent = caseEventRepository.save(caseEvent);
        log.info("Successfully updated eventId: {}", eventId);
        return caseEventMapper.toResponse(savedCaseEvent, findDeadlines(caseEvent));
    }

    @Transactional
    public void deleteCaseEvent(
            CaseId caseId,
            CaseEventId eventId,
            OrganizationId organizationId,
            UserId deletedById,
            String deleteReason) {
        log.debug("Deleting eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = findCaseEvent(eventId, caseId, organizationId);

        softDeleteManager.deleteEntity(caseEvent, deletedById.id(), deleteReason);

        // SEN-29 AC1: подія може мати кілька дедлайнів (по одному на кожне застосовне
        // правило) - видаляється (софт) кожен з них, а не лише "перший знайдений".
        caseEventDeadlines.deleteForCaseEvent(caseEvent.getId(), deletedById.id(), deleteReason);

        caseEventRepository.save(caseEvent);
        log.info("Successfully deleted eventId: {}", eventId);
    }

    @Transactional
    public void restoreCaseEvent(
            CaseId caseId, CaseEventId eventId, OrganizationId organizationId, UserId restoredById) {
        log.debug("Restoring eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = caseEventRepository
                .findDeletedByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new CaseEventNotFoundException(eventId.id()));

        softDeleteManager.restoreEntity(caseEvent, restoredById.id());
        caseEventRepository.save(caseEvent);
        log.info("Successfully restored eventId: {}", eventId);
    }

    // ---- CaseEventService (api) ---------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<TriggeringEvent> findTriggeringEvent(long caseEventId, long caseId, long organizationId) {
        return caseEventRepository.findByIdAndCaseIdAndOrganizationId(caseEventId, caseId, organizationId)
                .map(caseEventMapper::toTriggeringEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TriggeringEvent> findTriggeringEvents(Collection<Long> caseEventIds) {
        if (caseEventIds.isEmpty()) {
            return List.of();
        }
        return caseEventRepository.findAllWithCaseByIdIn(caseEventIds).stream()
                .map(caseEventMapper::toTriggeringEvent)
                .toList();
    }

    @Override
    @Transactional
    public void registerRegistryDocument(
            CaseId caseId, OrganizationId organizationId, CaseEventAutoRegisterRequest request) {
        registerRegistryCaseEvent(caseId, organizationId, request);
    }

    // ---- helpers ------------------------------------------------------------------------

    private Case findCase(CaseId caseId, OrganizationId organizationId) {
        return caseRepository.findByIdAndOrganizationId(caseId.id(), organizationId.id())
                .orElseThrow(() -> new CaseNotFoundException(caseId.id()));
    }

    private CaseEvent findCaseEvent(CaseEventId eventId, CaseId caseId, OrganizationId organizationId) {
        return caseEventRepository
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new CaseEventNotFoundException(eventId.id()));
    }

    private List<CaseEventDeadline> regenerateDeadlines(CaseEvent caseEvent, Long changedBy) {
        return caseEventDeadlines.regenerate(caseEventMapper.toTriggeringEvent(caseEvent), changedBy);
    }

    private List<CaseEventDeadline> findDeadlines(CaseEvent caseEvent) {
        return caseEventDeadlines.findByCaseEventIds(List.of(caseEvent.getId()))
                .getOrDefault(caseEvent.getId(), List.of());
    }
}
