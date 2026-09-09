package com.lisovskyi.core_service.deadline.finder;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeadlineFinder extends EntityFinder<Deadline, Long> {

    List<Deadline> findAllByTriggeringEventIdIn(List<Long> triggeringEventIds);

    List<Deadline> findAllByTriggeringEvent(CaseEvent triggeringEvent);

    Optional<Deadline> findByTriggeringEventAndRule(CaseEvent triggeringEvent, DeadlineRule rule);

    Deadline findByIdAndCaseIdAndOrganizationId(Long id, Long caseId, Long organizationId);

    Page<Deadline> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable);

    Page<Deadline> findAllByTriggeringEventIdAndCaseIdAndOrganizationId(Long triggeringEventId, Long caseId, Long organizationId, Pageable pageable);

    // SEN-26: CaseEvent-и всіх PENDING дедлайнів, чиє вікно [startsOn, dueOn] накриває date -
    // саме те, що DeadlineListener.onHolidayChanged передає в DeadlineGenerator.recalcAllDeadlines.
    List<CaseEvent> findTriggeringEventsOfPendingDeadlinesCovering(LocalDate date);
}
