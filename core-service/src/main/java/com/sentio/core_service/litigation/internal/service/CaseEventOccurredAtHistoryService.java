package com.sentio.core_service.litigation.internal.service;

import com.sentio.core_service.litigation.internal.controller.dto.CaseEventOccurredAtHistoryResponse;
import com.sentio.core_service.litigation.api.service.CaseService;
import com.sentio.core_service.litigation.internal.mapper.CaseEventMapper;
import com.sentio.core_service.litigation.internal.repository.CaseEventOccurredAtHistoryRepository;
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

    private final CaseEventOccurredAtHistoryRepository caseEventOccurredAtHistoryRepository;
    private final CaseEventMapper caseEventMapper;
    private final CaseService caseService;

    @Transactional(readOnly = true)
    public PageResponse<CaseEventOccurredAtHistoryResponse> getAllCaseEventOccurredAtHistories(
            CaseId caseId, CaseEventId caseEventId, OrganizationId organizationId, Pageable pageable) {
        log.debug(
                "Fetching case event occurredAt histories for caseId: {}, eventId: {}, orgId: {}",
                caseId,
                caseEventId,
                organizationId);
        caseService.assertCaseExists(caseId.id(), organizationId.id());

        return PageResponse.of(caseEventOccurredAtHistoryRepository
                .findAllByCaseEventIdAndCaseIdAndOrganizationId(
                        caseEventId.id(), caseId.id(), organizationId.id(), pageable)
                .map(caseEventMapper::toResponse));
    }
}
