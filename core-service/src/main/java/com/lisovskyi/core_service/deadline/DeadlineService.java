package com.lisovskyi.core_service.deadline;

import com.lisovskyi.core_service.audit_log.AuditLogService;
import com.lisovskyi.core_service.audit_log.enums.EntityType;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.finder.CaseFinder;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.finder.CaseEventFinder;
import com.lisovskyi.core_service.deadline.dto.request.DeadlineManualRegisterRequest;
import com.lisovskyi.core_service.deadline.dto.request.DeadlineRejectRequest;
import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import com.lisovskyi.core_service.deadline.enums.DeadlineSource;
import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
import com.lisovskyi.core_service.deadline.exception.DeadlineNotRejectableException;
import com.lisovskyi.core_service.deadline.finder.DeadlineFinder;
import com.lisovskyi.core_service.deadline.mapper.DeadlineMapper;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.deadline.DeadlineId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import io.micrometer.core.annotation.Timed;
import java.time.Instant;
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
    private final DeadlineFinder deadlineFinder;
    private final CaseFinder caseFinder;
    private final CaseEventFinder caseEventFinder;
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
        caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        Page<DeadlineResponse> deadlines;
        if (triggeringEventId == null) {
            deadlines = deadlineFinder
                    .findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id(), pageable)
                    .map(deadlineMapper::toResponse);
        } else {
            deadlines = deadlineFinder
                    .findAllByTriggeringEventIdAndCaseIdAndOrganizationId(
                            triggeringEventId.id(), caseId.id(), organizationId.id(), pageable)
                    .map(deadlineMapper::toResponse);
        }

        return PageResponse.of(deadlines);
    }

    // SEN-29 AC3: юрист додає строк вручну - без DeadlineRule і, за бажанням, без прив'язки до
    // конкретної події. triggeringEventId у запиті - JsonNullable, а не обов'язковий шлях, тож
    // тут явно розпаковуємо його (.orElse(null)), а не покладаємось на серіалізацію напряму:
    // Deadline.triggeringEvent - ManyToOne на CaseEvent, не на Long, тож самого id недостатньо.
    @Transactional
    public DeadlineResponse createManualDeadline(
            CaseId caseId,
            OrganizationId organizationId,
            UserId createdBy,
            DeadlineManualRegisterRequest request
    ) {
        log.debug("Creating manual deadline for caseId: {}", caseId);
        Case case_ = caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        // findByIdAndCaseIdAndOrganizationId сама перевіряє, що вказана подія належить саме
        // цій справі й організації (інакше - ResourceNotFoundException), тож окремої звірки
        // caseEvent.getCase_().getId() тут не треба.
        CaseEvent triggeringEvent = request.triggeringEventId()
                .map(eventId -> caseEventFinder.findByIdAndCaseIdAndOrganizationId(eventId, caseId.id(), organizationId.id()))
                .orElse(null);

        Deadline deadline = Deadline.builder()
                .organizationId(organizationId.id())
                .case_(case_)
                .triggeringEvent(triggeringEvent)
                // MANUAL - за визначенням без DeadlineRule: rule/ruleVersion і решта знімкових
                // полів (durationValue/durationUnit/dayKind/baseDate/naiveDueOn) лишаються null,
                // так само як і для рядків, порахованих до SEN-28 - DeadlineMapper.explanation()
                // це вже враховує й просто не будує пояснення, коли будь-яке з них відсутнє.
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
        return deadlineMapper.toResponse(savedDeadline);
    }

    // SEN-29 AC4: юрист відхиляє автоматично порахований строк з причиною. Подія-тригер і сам
    // рядок Deadline лишаються (не soft-delete, як deleteCaseEvent робить із власним дедлайном) -
    // лише статус і поля rejected* фіксують, що юрист свідомо вирішив "цей строк не рахувати", а
    // не що система про нього просто не дізналась.
    @Transactional
    public DeadlineResponse rejectDeadline(
            CaseId caseId,
            DeadlineId deadlineId,
            OrganizationId organizationId,
            UserId rejectedBy,
            DeadlineRejectRequest request) {
        log.debug("Rejecting deadlineId: {} for caseId: {}", deadlineId, caseId);
        Deadline deadline = deadlineFinder.findByIdAndCaseIdAndOrganizationId(deadlineId.id(), caseId.id(), organizationId.id());

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
        return deadlineMapper.toResponse(savedDeadline);
    }
}
