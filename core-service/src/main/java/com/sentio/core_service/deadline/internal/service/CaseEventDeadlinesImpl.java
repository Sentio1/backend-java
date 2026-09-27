package com.sentio.core_service.deadline.internal.service;

import com.sentio.core_service.common.model.SoftDeleteManager;
import com.sentio.core_service.deadline.internal.engine.DeadlineEngine;
import com.sentio.core_service.deadline.internal.model.Deadline;
import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import com.sentio.core_service.deadline.internal.repository.DeadlineRepository;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.core_service.litigation.api.spi.CaseEventDeadline;
import com.sentio.core_service.litigation.api.spi.CaseEventDeadlines;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The deadline side of litigation's CaseEventDeadlines SPI - see its javadoc for why it exists. */
@Service
@RequiredArgsConstructor
public class CaseEventDeadlinesImpl implements CaseEventDeadlines {

    private final DeadlineEngine deadlineEngine;
    private final DeadlineRepository deadlineRepository;
    private final SoftDeleteManager softDeleteManager;

    @Override
    @Transactional
    public List<CaseEventDeadline> regenerate(TriggeringEvent event, @Nullable Long changedBy) {
        return deadlineEngine.generateDeadline(event, changedBy).stream()
                .map(CaseEventDeadlinesImpl::toCaseEventDeadline)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<CaseEventDeadline>> findByCaseEventIds(Collection<Long> caseEventIds) {
        if (caseEventIds.isEmpty()) {
            return Map.of();
        }
        return deadlineRepository.findAllByTriggeringEventIdIn(caseEventIds).stream()
                .collect(Collectors.groupingBy(
                        Deadline::getTriggeringEventId,
                        Collectors.mapping(CaseEventDeadlinesImpl::toCaseEventDeadline, Collectors.toList())));
    }

    @Override
    @Transactional
    public void deleteForCaseEvent(long caseEventId, long deletedBy, @Nullable String deleteReason) {
        deadlineRepository.findAllByTriggeringEventId(caseEventId).forEach(deadline -> {
            softDeleteManager.deleteEntity(deadline, deletedBy, deleteReason);
            deadlineRepository.save(deadline);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findDeadlineIdsByCase(long caseId, long organizationId) {
        return deadlineRepository.findIdsByCaseIdAndOrganizationId(caseId, organizationId);
    }

    private static CaseEventDeadline toCaseEventDeadline(Deadline deadline) {
        return new CaseEventDeadline(deadline.getId(), deadline.getStatus() == DeadlineStatus.REJECTED);
    }
}
