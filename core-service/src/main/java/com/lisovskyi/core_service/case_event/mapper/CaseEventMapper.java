package com.lisovskyi.core_service.case_event.mapper;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventAutoRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.request.CaseEventManualRegisterRequest;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventOccurredAtHistoryResponse;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventResponse;
import com.lisovskyi.core_service.case_event.enums.DeadlineResolution;
import com.lisovskyi.core_service.case_event_occurred_at_history.CaseEventOccurredAtHistory;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.enums.DeadlineStatus;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CaseEventMapper {

    // Приймає самі сутності Deadline (не готові id) - deadlineIds і deadlineResolution обидва
    // обчислюються з того самого списку нижче, той самий підхід, що й explanation() у
    // DeadlineMapper: похідні поля відповіді складаються в одному місці, а не розкидані по
    // викликачах CaseEventService.
    @Mapping(target = "id", source = "caseEvent.id")
    @Mapping(target = "caseId", source = "caseEvent.case_.id")
    @Mapping(target = "deadlineIds", expression = "java(deadlineIds(deadlines))")
    @Mapping(target = "deadlineResolution", expression = "java(deadlineResolution(deadlines))")
    CaseEventResponse toResponse(CaseEvent caseEvent, List<Deadline> deadlines);

    default List<Long> deadlineIds(List<Deadline> deadlines) {
        return deadlines.stream().map(Deadline::getId).toList();
    }

    // SEN-29 AC2: NO_RULE_MATCHED - жодного дедлайна взагалі; REJECTED - дедлайни були, але всі
    // до одного відхилені (AC4); інакше RULE_APPLIED (є хоч один невідхилений).
    default DeadlineResolution deadlineResolution(List<Deadline> deadlines) {
        if (deadlines.isEmpty()) {
            return DeadlineResolution.NO_RULE_MATCHED;
        }
        boolean allRejected = deadlines.stream().allMatch(d -> d.getStatus() == DeadlineStatus.REJECTED);
        return allRejected ? DeadlineResolution.REJECTED : DeadlineResolution.RULE_APPLIED;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "case_", source = "case_")
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
    CaseEvent toEntity(CaseEventAutoRegisterRequest request, Long organizationId, Case case_);
}
