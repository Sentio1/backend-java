package com.lisovskyi.core_service.deadline.finder;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.deadline.Deadline;
import com.sentio.shared.entity.finder.EntityFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeadlineFinder extends EntityFinder<Deadline, Long> {

    List<Deadline> findAllByTriggeringEventIdIn(List<Long> triggeringEventIds);

    Optional<Deadline> findByTriggeringEvent(CaseEvent triggeringEvent);

    Optional<Long> findIdByTriggeringEventId(Long triggeringEventId);

    Page<Deadline> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable);

    Page<Deadline> findAllByTriggeringEventIdAndCaseIdAndOrganizationId(Long triggeringEventId, Long caseId, Long organizationId, Pageable pageable);

    // SEN-26: CaseEvent-и всіх PENDING дедлайнів, чиє вікно [startsOn, dueOn] накриває date -
    // саме те, що DeadlineListener.onHolidayChanged передає в DeadlineGenerator.recalcAllDeadlines.
    List<CaseEvent> findTriggeringEventsOfPendingDeadlinesCovering(LocalDate date);
}
