package com.sentio.core_service.litigation.internal.mapper;

import com.sentio.core_service.litigation.internal.model.Case;
import com.sentio.core_service.litigation.internal.model.CaseEvent;
import com.sentio.core_service.litigation.api.dto.CaseEventAutoRegisterRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CaseEventManualRegisterRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CaseEventOccurredAtHistoryResponse;
import com.sentio.core_service.litigation.internal.controller.dto.CaseEventResponse;
import com.sentio.core_service.litigation.internal.enums.DeadlineResolution;
import com.sentio.core_service.litigation.internal.model.CaseEventOccurredAtHistory;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.core_service.litigation.api.spi.CaseEventDeadline;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CaseEventMapper {

    // Приймає дедлайни події (з модуля deadline, через SPI CaseEventDeadlines) - deadlineIds і
    // deadlineResolution обидва обчислюються з того самого списку нижче: похідні поля відповіді
    // складаються в одному місці, а не розкидані по викликачах CaseEventServiceImpl.
    @Mapping(target = "id", source = "caseEvent.id")
    @Mapping(target = "caseId", source = "caseEvent.case_.id")
    @Mapping(target = "deadlineIds", expression = "java(deadlineIds(deadlines))")
    @Mapping(target = "deadlineResolution", expression = "java(deadlineResolution(deadlines))")
    CaseEventResponse toResponse(CaseEvent caseEvent, List<CaseEventDeadline> deadlines);

    default List<Long> deadlineIds(List<CaseEventDeadline> deadlines) {
        return deadlines.stream().map(CaseEventDeadline::id).toList();
    }

    // SEN-29 AC2: NO_RULE_MATCHED - жодного дедлайна взагалі; REJECTED - дедлайни були, але всі
    // до одного відхилені (AC4); інакше RULE_APPLIED (є хоч один невідхилений).
    default DeadlineResolution deadlineResolution(List<CaseEventDeadline> deadlines) {
        if (deadlines.isEmpty()) {
            return DeadlineResolution.NO_RULE_MATCHED;
        }
        boolean allRejected = deadlines.stream().allMatch(CaseEventDeadline::rejected);
        return allRejected ? DeadlineResolution.REJECTED : DeadlineResolution.RULE_APPLIED;
    }

    default TriggeringEvent toTriggeringEvent(CaseEvent caseEvent) {
        return new TriggeringEvent(
                caseEvent.getId(),
                caseEvent.getCase_().getId(),
                caseEvent.getOrganizationId(),
                caseEvent.getEventCode(),
                caseEvent.getOccurredAt(),
                caseEvent.getCase_().getProcedure(),
                caseEvent.getCase_().getCourtId());
    }

    // Case case_ is a second SOURCE here, not just the value for the case_ field: without the
    // explicit ignores below MapStruct copies every same-named property from the CASE onto the
    // new event - createdBy (the case's author became the event's "author", even for events from
    // the registry that have none), deletedBy/deleteReason/restoredAt/restoredBy. Those are set by
    // the service (createdBy, source, registry fields) or stay empty.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "case_", source = "case_")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "registryDocumentId", ignore = true)
    @Mapping(target = "registryDocumentTextRef", ignore = true)
    CaseEvent toEntity(CaseEventManualRegisterRequest request, Long organizationId, Case case_);

    @Mapping(target = "caseEventId", source = "caseEvent.id")
    CaseEventOccurredAtHistoryResponse toResponse(CaseEventOccurredAtHistory caseEventOccurredAtHistory);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "case_", source = "case_")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    @Mapping(target = "source", ignore = true)
    CaseEvent toEntity(CaseEventAutoRegisterRequest request, Long organizationId, Case case_);
}
