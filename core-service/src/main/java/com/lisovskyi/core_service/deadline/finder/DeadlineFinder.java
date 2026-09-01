package com.lisovskyi.core_service.deadline.finder;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.deadline.Deadline;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DeadlineFinder extends EntityFinder<Deadline, Long> {

    List<Deadline> findAllByTriggeringEventIdIn(List<Long> triggeringEventIds);

    Optional<Deadline> findByTriggeringEvent(CaseEvent triggeringEvent);

    Optional<Long> findIdByTriggeringEventId(Long triggeringEventId);

    Page<Deadline> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable);

    Page<Deadline> findAllByTriggeringEventIdAndCaseIdAndOrganizationId(Long triggeringEventId, Long caseId, Long organizationId, Pageable pageable);
}
