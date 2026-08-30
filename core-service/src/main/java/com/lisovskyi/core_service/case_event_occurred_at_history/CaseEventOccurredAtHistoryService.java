package com.lisovskyi.core_service.case_event_occurred_at_history;

import com.lisovskyi.core_service.case_.service.CaseFinder;
import com.lisovskyi.core_service.case_event.dto.response.CaseEventOccurredAtHistoryResponse;
import com.lisovskyi.core_service.case_event.mapper.CaseEventMapper;
import com.sentio.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaseEventOccurredAtHistoryService {

    private final CaseEventOccurredAtHistoryRepository caseEventOccurredAtHistoryRepository;
    private final CaseEventMapper caseEventMapper;
    private final CaseFinder caseFinder;

    @Transactional(readOnly = true)
    public PageResponse<CaseEventOccurredAtHistoryResponse> getAllCaseEventOccurredAtHistories(
            Long caseId, Long organizationId, Pageable pageable) {
        log.debug("Fetching case event occurredAt histories for caseId: {}, orgId: {}", caseId, organizationId);
        caseFinder.findByIdAndOrganizationId(caseId, organizationId);

        return PageResponse.of(caseEventOccurredAtHistoryRepository
                .findAllByCaseIdAndOrganizationId(caseId, organizationId, pageable)
                .map(caseEventMapper::toResponse));
    }
}
