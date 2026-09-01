package com.lisovskyi.core_service.case_event_occurred_at_history.finder;

import com.lisovskyi.core_service.case_event_occurred_at_history.CaseEventOccurredAtHistory;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CaseEventOccurredAtHistoryFinder extends EntityFinder<CaseEventOccurredAtHistory, Long> {

    Page<CaseEventOccurredAtHistory> findAllByCaseEventIdAndCaseIdAndOrganizationId(
            Long caseEventId, Long caseId, Long organizationId, Pageable pageable);
}
