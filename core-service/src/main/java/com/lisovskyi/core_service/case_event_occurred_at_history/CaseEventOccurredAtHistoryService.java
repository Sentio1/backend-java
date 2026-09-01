package com.lisovskyi.core_service.case_event_occurred_at_history;

import com.lisovskyi.core_service.case_.service.finder.CaseFinder;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventOccurredAtHistoryResponse;
import com.lisovskyi.core_service.case_event.mapper.CaseEventMapper;
import com.lisovskyi.core_service.case_event_occurred_at_history.finder.CaseEventOccurredAtHistoryFinder;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaseEventOccurredAtHistoryService {

    private final CaseEventOccurredAtHistoryFinder caseEventOccurredAtHistoryFinder;
    private final CaseEventMapper caseEventMapper;
    private final CaseFinder caseFinder;

    @Transactional(readOnly = true)
    public PageResponse<CaseEventOccurredAtHistoryResponse> getAllCaseEventOccurredAtHistories(
            CaseId caseId, CaseEventId caseEventId, OrganizationId organizationId, Pageable pageable) {
        log.debug(
                "Fetching case event occurredAt histories for caseId: {}, eventId: {}, orgId: {}",
                caseId,
                caseEventId,
                organizationId);
        caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        return PageResponse.of(caseEventOccurredAtHistoryFinder
                .findAllByCaseEventIdAndCaseIdAndOrganizationId(
                        caseEventId.id(), caseId.id(), organizationId.id(), pageable)
                .map(caseEventMapper::toResponse));
    }
}
