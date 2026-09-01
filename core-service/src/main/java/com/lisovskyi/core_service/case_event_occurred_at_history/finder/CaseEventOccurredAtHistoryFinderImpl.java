package com.lisovskyi.core_service.case_event_occurred_at_history.finder;

import com.lisovskyi.core_service.case_event_occurred_at_history.CaseEventOccurredAtHistory;
import com.lisovskyi.core_service.case_event_occurred_at_history.CaseEventOccurredAtHistoryRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseEventOccurredAtHistoryFinderImpl extends AbstractEntityFinder<CaseEventOccurredAtHistory, Long>
        implements CaseEventOccurredAtHistoryFinder {

    private final CaseEventOccurredAtHistoryRepository caseEventOccurredAtHistoryRepository;

    @Override
    protected JpaRepository<CaseEventOccurredAtHistory, Long> getRepository() {
        return caseEventOccurredAtHistoryRepository;
    }

    @Override
    protected String getEntityName() {
        return "CaseEventOccurredAtHistory";
    }

    @Override
    public Page<CaseEventOccurredAtHistory> findAllByCaseEventIdAndCaseIdAndOrganizationId(
            Long caseEventId, Long caseId, Long organizationId, Pageable pageable) {
        requireNonNull(caseEventId, caseId, organizationId);
        return caseEventOccurredAtHistoryRepository.findAllByCaseEventIdAndCaseIdAndOrganizationId(
                caseEventId, caseId, organizationId, pageable);
    }
}
