package com.lisovskyi.core_service.deadline.finder;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline.DeadlineStatus;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeadlineFinderImpl extends AbstractEntityFinder<Deadline, Long> implements DeadlineFinder {

    private final DeadlineRepository deadlineRepository;

    @Override
    protected JpaRepository<Deadline, Long> getRepository() {
        return deadlineRepository;
    }

    @Override
    protected String getEntityName() {
        return "Deadline";
    }

    @Override
    public List<Deadline> findAllByTriggeringEventIdIn(List<Long> triggeringEventIds) {
        requireNonNull(triggeringEventIds);
        return deadlineRepository.findAllByTriggeringEventIdIn(triggeringEventIds);
    }

    @Override
    public Optional<Deadline> findByTriggeringEvent(CaseEvent triggeringEvent) {
        requireNonNull(triggeringEvent);
        return deadlineRepository.findByTriggeringEvent(triggeringEvent);
    }

    @Override
    public Optional<Long> findIdByTriggeringEventId(Long triggeringEventId) {
        requireNonNull(triggeringEventId);
        return deadlineRepository.findIdByTriggeringEventId(triggeringEventId);
    }

    @Override
    public Page<Deadline> findAllByCaseIdAndOrganizationId(Long caseId, Long organizationId, Pageable pageable) {
        return findAll(caseId, organizationId, pageable, deadlineRepository::findAllByCaseIdAndOrganizationId);
    }

    @Override
    public Page<Deadline> findAllByTriggeringEventIdAndCaseIdAndOrganizationId(Long triggeringEventId, Long caseId, Long organizationId, Pageable pageable) {
        requireNonNull(triggeringEventId, caseId, organizationId);
        return deadlineRepository.findAllByTriggeringEventIdAndCaseIdAndOrganizationId(triggeringEventId, caseId, organizationId, pageable);
    }

    @Override
    public List<CaseEvent> findTriggeringEventsOfPendingDeadlinesCovering(LocalDate date) {
        requireNonNull(date);
        return deadlineRepository.findTriggeringEventsByStatusAndWindowCovering(DeadlineStatus.PENDING, date);
    }
}
