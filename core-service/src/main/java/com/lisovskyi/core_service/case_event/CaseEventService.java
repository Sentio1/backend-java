package com.lisovskyi.core_service.case_event;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.core_service.audit_log.AuditLogService;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventAutoRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventOccurredAtChangeRequest;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventUpdateRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.case_event.enums.Source;
import com.lisovskyi.core_service.case_event.mapper.CaseEventMapper;
import com.lisovskyi.core_service.case_event_occurred_at_history.CaseEventOccurredAtHistory;
import com.lisovskyi.core_service.case_event_occurred_at_history.CaseEventOccurredAtHistoryRepository;
import com.lisovskyi.core_service.case_event.finder.CaseEventFinder;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline.finder.DeadlineFinder;
import com.lisovskyi.core_service.deadline_processor.DeadlineEngine;
import com.lisovskyi.core_service.entity.SoftDeleteManager;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
public class CaseEventService {

    private final CaseEventRepository caseEventRepository;
    private final CaseEventFinder caseEventFinder;
    private final DeadlineRepository deadlineRepository;
    private final DeadlineFinder deadlineFinder;
    private final CaseEventOccurredAtHistoryRepository caseEventOccurredAtHistoryRepository;

    private final CaseEventMapper caseEventMapper;
    private final CaseFinder caseFinder;
    private final DeadlineEngine deadlineEngine;
    private final AuditLogService auditLogService;

    private final SoftDeleteManager softDeleteManager;

    @Transactional(readOnly = true)
    public PageResponse<CaseEventResponse> getAllCaseEvents(
            CaseId caseId, OrganizationId organizationId, EventCode eventCode, Pageable pageable) {
        log.debug("Fetching case events for caseId: {}, orgId: {}, eventCode: {}", caseId, organizationId, eventCode);
        caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        Page<CaseEvent> caseEvents = caseEventFinder.findAllByCaseIdAndOrganizationId(
                caseId.id(), organizationId.id(), eventCode, pageable);
        List<Long> caseEventIds = caseEvents.map(CaseEvent::getId).toList();

        Map<Long, Long> deadlineIdByCaseEventId = deadlineFinder.findAllByTriggeringEventIdIn(caseEventIds).stream()
                .collect(Collectors.toMap(
                        deadline -> deadline.getTriggeringEvent().getId(), Deadline::getId));

        return PageResponse.of(caseEvents.map(
                caseEvent -> caseEventMapper.toResponse(caseEvent, deadlineIdByCaseEventId.get(caseEvent.getId()))));
    }

    @Transactional(readOnly = true)
    public CaseEventResponse getCaseEventById(CaseId caseId, CaseEventId eventId, OrganizationId organizationId) {
        log.debug("Fetching case event id: {} for caseId: {}, orgId: {}", eventId, caseId, organizationId);
        CaseEvent caseEvent = caseEventFinder
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id());

        Long deadlineId = findDeadlineId(caseEvent);
        return caseEventMapper.toResponse(caseEvent, deadlineId);
    }

    // Самостійно юрист створює case event
    @Transactional
    public CaseEventResponse registerCaseEvent(
            CaseId caseId, OrganizationId organizationId, UserId createdBy, CaseEventManualRegisterRequest request) {
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        CaseEvent caseEvent = caseEventMapper.toEntity(request, organizationId.id(), case_);
        caseEvent.setCreatedBy(createdBy.id());
        caseEvent.setSource(Source.MANUAL);
        caseEvent.setRegisteredAt(Instant.now());

        CaseEvent savedCaseEvent = caseEventRepository.saveAndFlush(caseEvent);
        Long deadlineId = deadlineEngine.generateDeadline(savedCaseEvent, createdBy.id());
        log.info("Manually registered case event id: {} for caseId: {}", savedCaseEvent.getId(), caseId);
        return caseEventMapper.toResponse(savedCaseEvent, deadlineId);
    }

    // Приходить з Go сервісу
    @Transactional
    public CaseEventResponse registerRegistryCaseEvent(
            CaseId caseId, OrganizationId organizationId, CaseEventAutoRegisterRequest request) {
        log.debug("Auto-registering registry event for caseId: {}", caseId);
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        CaseEvent caseEvent = caseEventMapper.toEntity(request, organizationId.id(), case_);
        caseEvent.setSource(Source.REGISTRY);
        caseEvent.setRegistryDocumentId(request.registryDocumentId());
        caseEvent.setRegisteredAt(Instant.now());

        try {
            CaseEvent savedCaseEvent = caseEventRepository.saveAndFlush(caseEvent);
            // changedBy = null: автоматична реєстрація з Go-сервісу, немає людини-автора
            // (SERVICE-роль з власною ідентичністю ще не реалізована - README/SEN-33), тож зміна
            // dueOn (якщо була) потрапить в audit_log з ChangedByType.SYSTEM, а не з вигаданим
            // системним юзером (див. DeadlineEngine.generateDeadline).
            Long deadlineId = deadlineEngine.generateDeadline(savedCaseEvent, null);
            log.info("Successfully registered registry event id: {} for caseId: {}", savedCaseEvent.getId(), caseId);
            return caseEventMapper.toResponse(savedCaseEvent, deadlineId);
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
        CaseEvent caseEvent = caseEventFinder
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id());

        Instant oldOccurredAt = caseEvent.getOccurredAt();
        if (oldOccurredAt.equals(request.newOccurredAt())) {
            log.info("changeOccurredAt called with unchanged date for event id={}, skipping", caseEvent.getId());
            Long deadlineId = findDeadlineId(caseEvent);
            return caseEventMapper.toResponse(caseEvent, deadlineId);
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

        Long deadlineId = deadlineEngine.generateDeadline(savedCaseEvent, changedById.id());
        log.info(
                "Changed occurredAt for eventId: {} (old: {}, new: {})",
                eventId,
                oldOccurredAt,
                request.newOccurredAt());
        return caseEventMapper.toResponse(savedCaseEvent, deadlineId);
    }

    @Transactional
    public CaseEventResponse updateCaseEvent(
            CaseId caseId,
            CaseEventId eventId,
            OrganizationId organizationId,
            UserId changedById,
            CaseEventUpdateRequest request) {
        log.debug("Updating case eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = caseEventFinder
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id());

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
        Long deadlineId = findDeadlineId(caseEvent);
        log.info("Successfully updated eventId: {}", eventId);
        return caseEventMapper.toResponse(savedCaseEvent, deadlineId);
    }

    @Transactional
    public void deleteCaseEvent(
            CaseId caseId,
            CaseEventId eventId,
            OrganizationId organizationId,
            UserId deletedById,
            String deleteReason) {
        log.debug("Deleting eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = caseEventFinder
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id());

        softDeleteManager.deleteEntity(caseEvent, deletedById.id(), deleteReason);

        deadlineFinder.findByTriggeringEvent(caseEvent).ifPresent(deadline -> {
            softDeleteManager.deleteEntity(deadline, deletedById.id(), deleteReason);
            deadlineRepository.save(deadline);
        });

        caseEventRepository.save(caseEvent);
        log.info("Successfully deleted eventId: {}", eventId);
    }

    @Transactional
    public void restoreCaseEvent(
            CaseId caseId, CaseEventId eventId, OrganizationId organizationId, UserId restoredById) {
        log.debug("Restoring eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = caseEventFinder
                .findDeletedByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("CaseEvent", "id", eventId));

        softDeleteManager.restoreEntity(caseEvent, restoredById.id());
        caseEventRepository.save(caseEvent);
        log.info("Successfully restored eventId: {}", eventId);
    }

    private Long findDeadlineId(CaseEvent caseEvent) {
        return deadlineFinder.findIdByTriggeringEventId(caseEvent.getId()).orElse(null);
    }
}
