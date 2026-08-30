package com.lisovskyi.core_service.case_event;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_.service.CaseFinder;
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
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.EventDateResolver;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline.WorkingDayCalculator;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.DeadlineRuleRepository;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import com.lisovskyi.core_service.entity.SoftDeletable;
import com.lisovskyi.core_service.holiday.Holiday;
import com.lisovskyi.core_service.holiday.HolidayRepository;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class CaseEventService implements SoftDeletable {

    private final CaseEventRepository caseEventRepository;
    private final DeadlineRepository deadlineRepository;
    private final DeadlineRuleRepository deadlineRuleRepository;
    private final HolidayRepository holidayRepository;
    private final CaseEventOccurredAtHistoryRepository caseEventOccurredAtHistoryRepository;

    private final CaseEventMapper caseEventMapper;
    private final CaseFinder caseFinder;

    @Transactional(readOnly = true)
    public PageResponse<CaseEventResponse> getAllCaseEvents(
            CaseId caseId, OrganizationId organizationId, EventCode eventCode, Pageable pageable) {
        log.debug("Fetching case events for caseId: {}, orgId: {}, eventCode: {}", caseId, organizationId, eventCode);
        caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        Page<CaseEvent> caseEvents = caseEventRepository.findAllByCaseIdAndOrganizationId(
                caseId.id(), organizationId.id(), eventCode, pageable);
        List<Long> caseEventIds = caseEvents.map(CaseEvent::getId).toList();

        Map<Long, Long> deadlineIdByCaseEventId = deadlineRepository.findAllByTriggeringEventIdIn(caseEventIds).stream()
                .collect(Collectors.toMap(
                        deadline -> deadline.getTriggeringEvent().getId(), Deadline::getId));

        return PageResponse.of(caseEvents.map(
                caseEvent -> caseEventMapper.toResponse(caseEvent, deadlineIdByCaseEventId.get(caseEvent.getId()))));
    }

    @Transactional(readOnly = true)
    public CaseEventResponse getCaseEventById(CaseId caseId, CaseEventId eventId, OrganizationId organizationId) {
        log.debug("Fetching case event id: {} for caseId: {}, orgId: {}", eventId, caseId, organizationId);
        CaseEvent caseEvent = caseEventRepository
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("CaseEvent", "id", eventId));

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
        Long deadlineId = deadlineEngine(savedCaseEvent);
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
            Long deadlineId = deadlineEngine(savedCaseEvent);
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
        CaseEvent caseEvent = caseEventRepository
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("CaseEvent", "id", eventId));

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

        Long deadlineId = deadlineEngine(savedCaseEvent);
        log.info(
                "Changed occurredAt for eventId: {} (old: {}, new: {})",
                eventId,
                oldOccurredAt,
                request.newOccurredAt());
        return caseEventMapper.toResponse(savedCaseEvent, deadlineId);
    }

    @Transactional
    public CaseEventResponse updateCaseEvent(
            CaseId caseId, CaseEventId eventId, OrganizationId organizationId, CaseEventUpdateRequest request) {
        log.debug("Updating case eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = caseEventRepository
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("CaseEvent", "id", eventId));

        if (request.title().isPresent()) {
            caseEvent.setTitle(request.title().get());
        }

        if (request.description().isPresent()) {
            caseEvent.setDescription(request.description().get());
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
        CaseEvent caseEvent = caseEventRepository
                .findByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("CaseEvent", "id", eventId));

        deleteEntity(caseEvent, deletedById.id(), deleteReason);

        deadlineRepository.findByTriggeringEvent(caseEvent).ifPresent(deadline -> {
            deleteEntity(deadline, deletedById.id(), deleteReason);
            deadlineRepository.save(deadline);
        });

        caseEventRepository.save(caseEvent);
        log.info("Successfully deleted eventId: {}", eventId);
    }

    @Transactional
    public void restoreCaseEvent(
            CaseId caseId, CaseEventId eventId, OrganizationId organizationId, UserId restoredById) {
        log.debug("Restoring eventId: {} for caseId: {}", eventId, caseId);
        CaseEvent caseEvent = caseEventRepository
                .findDeletedByIdAndCaseIdAndOrganizationId(eventId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new ResourceNotFoundException("CaseEvent", "id", eventId));

        restoreEntity(caseEvent, restoredById.id());
        caseEventRepository.save(caseEvent);
        log.info("Successfully restored eventId: {}", eventId);
    }

    Long deadlineEngine(CaseEvent caseEvent) {
        Court court = caseEvent.getCase_().getCourt();
        LocalDate occurredAt = EventDateResolver.toLocalDate(caseEvent.getOccurredAt(), court);

        ProcedureType procedure = caseEvent.getCase_().getProcedure();
        EventCode triggerEventCode = caseEvent.getEventCode();

        Optional<DeadlineRule> deadlineRuleOpt =
                deadlineRuleRepository.findByActiveRule(procedure, triggerEventCode, occurredAt);
        if (deadlineRuleOpt.isEmpty()) {
            // не помилка, просто нема правила
            return null;
        }

        DeadlineRule rule = deadlineRuleOpt.get();

        LocalDate startsOn =
                switch (rule.getCountFrom()) {
                    case CountFrom.NEXT_DAY -> occurredAt.plusDays(1);
                    case CountFrom.SAME_DAY -> occurredAt;
                };

        LocalDate dueOn =
                switch (rule.getDurationUnit()) {
                    case DurationUnit.DAY ->
                        switch (rule.getDayKind()) {
                            case CALENDAR -> startsOn.plusDays(rule.getDurationValue());
                            case WORKING -> {
                                short upperBound = (short) (rule.getDurationValue() * 2 + 10);
                                LocalDate upperBoundDate = startsOn.plusDays(upperBound);

                                List<Holiday> holidays =
                                        holidayRepository.findAllByDateBetween(startsOn, upperBoundDate);
                                Map<LocalDate, Boolean> holidayOverrides = new HashMap<>();
                                holidays.forEach(
                                        holiday -> holidayOverrides.put(holiday.getDate(), holiday.isWorking()));

                                yield WorkingDayCalculator.calculateDueOn(
                                        startsOn, rule.getDurationValue(), upperBoundDate, holidayOverrides);
                            }
                        };
                    case DurationUnit.MONTH -> startsOn.plusMonths(rule.getDurationValue());
                };

        Optional<Deadline> existingOpt = deadlineRepository.findByTriggeringEventAndRule(caseEvent, rule);

        Deadline deadline = existingOpt
                .map(existing -> {
                    log.info(
                            "Recalculating deadline id={} for event id={}: dueOn {} -> {}",
                            existing.getId(),
                            caseEvent.getId(),
                            existing.getDueOn(),
                            dueOn);

                    existing.setTitle(rule.getTitle());
                    existing.setLegalBasis(rule.getLegalBasis());
                    existing.setStartsOn(startsOn);
                    existing.setDueOn(dueOn);
                    return existing;
                })
                .orElseGet(() -> Deadline.builder()
                        .title(rule.getTitle())
                        .legalBasis(rule.getLegalBasis())
                        .organizationId(caseEvent.getOrganizationId())
                        .case_(caseEvent.getCase_())
                        .rule(rule)
                        .triggeringEvent(caseEvent)
                        .startsOn(startsOn)
                        .dueOn(dueOn)
                        .build());

        return deadlineRepository.save(deadline).getId();
    }

    private Long findDeadlineId(CaseEvent caseEvent) {
        return deadlineRepository.findIdByTriggeringEventId(caseEvent.getId()).orElse(null);
    }
}
