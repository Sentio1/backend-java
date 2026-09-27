package com.sentio.core_service.deadline.internal.service;

import com.sentio.core_service.audit.api.enums.EntityType;
import com.sentio.core_service.audit.api.service.AuditLogService;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineManualRegisterRequest;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineRejectRequest;
import com.sentio.core_service.deadline.internal.controller.dto.DeadlineResponse;
import com.sentio.core_service.deadline.internal.model.Deadline;
import com.sentio.core_service.deadline.internal.enums.DeadlineSource;
import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import com.sentio.core_service.deadline.internal.exception.DeadlineNotFoundException;
import com.sentio.core_service.deadline.internal.exception.DeadlineNotRejectableException;
import com.sentio.core_service.deadline.internal.mapper.DeadlineMapper;
import com.sentio.core_service.deadline.internal.repository.DeadlineRepository;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.litigation.api.service.CaseEventService;
import com.sentio.core_service.litigation.api.service.CaseService;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.deadline.DeadlineId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import io.micrometer.core.annotation.Timed;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeadlineService {

    private final DeadlineMapper deadlineMapper;
    private final DeadlineRepository deadlineRepository;
    private final CaseService caseService;
    private final CaseEventService caseEventService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    @Timed(value = "core-service.deadline.duration", description = "Time taken for deadline")
    public PageResponse<DeadlineResponse> getAllDeadlines(
            CaseId caseId, OrganizationId organizationId, CaseEventId triggeringEventId, Pageable pageable
    ) {
        log.debug(
                "Fetching deadlines for caseId: {}, orgId: {}, triggeringEventId: {}",
                caseId,
                organizationId,
                triggeringEventId
        );
        caseService.assertCaseExists(caseId.id(), organizationId.id());

        Page<Deadline> deadlines = triggeringEventId == null
                ? deadlineRepository.findAllByCaseIdAndOrganizationIdOrderByDueOn(
                        caseId.id(), organizationId.id(), pageable)
                : deadlineRepository.findAllByTriggeringEventIdAndCaseIdAndOrganizationIdOrderByDueOn(
                        triggeringEventId.id(), caseId.id(), organizationId.id(), pageable);

        return PageResponse.of(toResponses(deadlines));
    }

    @Transactional
    public DeadlineResponse createManualDeadline(
            CaseId caseId,
            OrganizationId organizationId,
            UserId createdBy,
            DeadlineManualRegisterRequest request
    ) {
        log.debug("Creating manual deadline for caseId: {}", caseId);
        caseService.assertCaseExists(caseId.id(), organizationId.id());

        TriggeringEvent triggeringEvent = request.triggeringEventId()
                .map(eventId -> caseEventService.findTriggeringEvent(eventId, caseId.id(), organizationId.id())
                        .orElseThrow(() -> new ResourceNotFoundException("CaseEvent", "id", eventId)))
                .orElse(null);

        Deadline deadline = Deadline.builder()
                .organizationId(organizationId.id())
                .caseId(caseId.id())
                .triggeringEventId(triggeringEvent != null ? triggeringEvent.id() : null)
                .rule(null)
                .ruleVersion(null)
                .source(DeadlineSource.MANUAL)
                .createdBy(createdBy.id())
                .title(request.title())
                .legalBasis(request.legalBasis().orElse(null))
                .startsOn(request.startsOn())
                .dueOn(request.dueOn())
                .note(request.note().orElse(null))
                .build();

        Deadline savedDeadline = deadlineRepository.save(deadline);
        log.info("Created manual deadline id={} for caseId: {}", savedDeadline.getId(), caseId);
        return deadlineMapper.toResponse(savedDeadline, triggeringEvent != null ? triggeringEvent.eventCode() : null);
    }

    @Transactional
    public DeadlineResponse rejectDeadline(
            CaseId caseId,
            DeadlineId deadlineId,
            OrganizationId organizationId,
            UserId rejectedBy,
            DeadlineRejectRequest request) {
        log.debug("Rejecting deadlineId: {} for caseId: {}", deadlineId, caseId);
        Deadline deadline = deadlineRepository
                .findByIdAndCaseIdAndOrganizationId(deadlineId.id(), caseId.id(), organizationId.id())
                .orElseThrow(() -> new DeadlineNotFoundException(deadlineId.id()));

        if (deadline.getStatus() != DeadlineStatus.PENDING) {
            throw new DeadlineNotRejectableException(DeadlineId.of(deadline.getId()), deadline.getStatus());
        }

        DeadlineStatus oldStatus = deadline.getStatus();
        deadline.setStatus(DeadlineStatus.REJECTED);
        deadline.setRejectedAt(Instant.now());
        deadline.setRejectedBy(rejectedBy.id());
        deadline.setRejectionReason(request.reason());

        Deadline savedDeadline = deadlineRepository.save(deadline);

        DeadlineId auditDeadlineId = DeadlineId.of(savedDeadline.getId());
        auditLogService.log(
                organizationId, EntityType.DEADLINE, auditDeadlineId, rejectedBy,
                "status", oldStatus.toString(), DeadlineStatus.REJECTED.toString());
        auditLogService.log(
                organizationId, EntityType.DEADLINE, auditDeadlineId, rejectedBy,
                "rejectionReason", null, request.reason());

        log.info("Rejected deadlineId: {} for caseId: {}", deadlineId, caseId);
        return toResponses(List.of(savedDeadline)).getFirst();
    }

    // The explanation mentions the triggering event's code, which lives in litigation - one
    // lookup for all deadlines of the page instead of one per deadline.
    private List<DeadlineResponse> toResponses(List<Deadline> deadlines) {
        Map<Long, EventCode> eventCodes = eventCodesOf(deadlines);
        return deadlines.stream()
                .map(deadline -> deadlineMapper.toResponse(deadline, eventCodes.get(deadline.getTriggeringEventId())))
                .toList();
    }

    private Page<DeadlineResponse> toResponses(Page<Deadline> deadlines) {
        Map<Long, EventCode> eventCodes = eventCodesOf(deadlines.getContent());
        return deadlines.map(deadline ->
                deadlineMapper.toResponse(deadline, eventCodes.get(deadline.getTriggeringEventId())));
    }

    private Map<Long, EventCode> eventCodesOf(List<Deadline> deadlines) {
        Set<Long> eventIds = deadlines.stream()
                .map(Deadline::getTriggeringEventId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return caseEventService.findTriggeringEvents(eventIds).stream()
                .collect(Collectors.toMap(TriggeringEvent::id, TriggeringEvent::eventCode));
    }
}
