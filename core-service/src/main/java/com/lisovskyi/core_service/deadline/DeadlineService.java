package com.lisovskyi.core_service.deadline;

import com.lisovskyi.core_service.case_.service.CaseFinder;
import com.lisovskyi.core_service.deadline.dto.response.DeadlineResponse;
import com.lisovskyi.core_service.deadline.mapper.DeadlineMapper;
import com.sentio.shared.dto.PageResponse;
import com.sentio.shared.entity.id.case_.CaseId;
import com.sentio.shared.entity.id.case_event.CaseEventId;
import com.sentio.shared.entity.id.organization.OrganizationId;
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

    private final DeadlineRepository deadlineRepository;
    private final DeadlineMapper deadlineMapper;
    private final CaseFinder caseFinder;

    @Transactional(readOnly = true)
    public PageResponse<DeadlineResponse> getAllDeadlines(CaseId caseId, OrganizationId organizationId, CaseEventId triggeringEventId, Pageable pageable) {
        log.debug("Fetching deadlines for caseId: {}, orgId: {}, triggeringEventId: {}", caseId, organizationId, triggeringEventId);
        caseFinder.findByIdAndOrganizationId(caseId.id(), organizationId.id());

        Page<DeadlineResponse> deadlines;
        if (triggeringEventId == null) {
            deadlines = deadlineRepository.findAllByCaseIdAndOrganizationId(caseId.id(), organizationId.id(), pageable)
                    .map(deadlineMapper::toResponse);
        } else {
            deadlines = deadlineRepository.findAllByTriggeringEventIdAndCaseIdAndOrganizationId(triggeringEventId.id(), caseId.id(), organizationId.id(), pageable)
                    .map(deadlineMapper::toResponse);
        }

        return PageResponse.of(deadlines);
    }
}
