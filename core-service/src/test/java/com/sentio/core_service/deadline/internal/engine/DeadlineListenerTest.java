package com.sentio.core_service.deadline.internal.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sentio.core_service.calendar.api.event.HolidayChangedEvent;
import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import com.sentio.core_service.deadline.internal.repository.DeadlineRepository;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.core_service.litigation.api.enums.EventCode;
import com.sentio.core_service.litigation.api.enums.ProcedureType;
import com.sentio.core_service.litigation.api.service.CaseEventService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SEN-26: onHolidayChanged is @TransactionalEventListener(AFTER_COMMIT) - Spring itself decides
 * whether/when to invoke it based on the surrounding transaction, so that plumbing isn't testable
 * here without a full Spring context. What's pinned down instead is the part that's easy to get
 * wrong on its own: which case events get recalculated for the changed date, and that
 * recalculation runs with changedBy = null (no human is responsible for this particular dueOn
 * shift - see the comment on the class).
 */
@ExtendWith(MockitoExtension.class)
class DeadlineListenerTest {

    @Mock
    private DeadlineRepository deadlineRepository;

    @Mock
    private CaseEventService caseEventService;

    @Mock
    private DeadlineEngine deadlineEngine;

    @InjectMocks
    private DeadlineListener deadlineListener;

    @Test
    void onHolidayChanged_recalculatesEventsOfPendingDeadlinesCoveringTheDate_withNoChangedByUser() {
        LocalDate changedDate = LocalDate.of(2026, 11, 14);
        TriggeringEvent caseEvent = new TriggeringEvent(
                7L, 1L, 1L, EventCode.DECISION, Instant.parse("2026-11-01T10:00:00Z"), ProcedureType.CIVIL, 3L);
        when(deadlineRepository.findTriggeringEventIdsByStatusAndWindowCovering(DeadlineStatus.PENDING, changedDate))
                .thenReturn(List.of(7L));
        when(caseEventService.findTriggeringEvents(List.of(7L))).thenReturn(List.of(caseEvent));

        deadlineListener.onHolidayChanged(new HolidayChangedEvent(changedDate));

        verify(deadlineEngine).generateDeadline(caseEvent, null);
    }

    @Test
    void onHolidayChanged_noAffectedDeadlines_recalculatesNothing() {
        LocalDate changedDate = LocalDate.of(2026, 11, 14);
        when(deadlineRepository.findTriggeringEventIdsByStatusAndWindowCovering(DeadlineStatus.PENDING, changedDate))
                .thenReturn(List.of());

        deadlineListener.onHolidayChanged(new HolidayChangedEvent(changedDate));

        verify(caseEventService, never()).findTriggeringEvents(anyCollection());
        verify(deadlineEngine, never()).generateDeadline(any(), isNull());
    }
}
