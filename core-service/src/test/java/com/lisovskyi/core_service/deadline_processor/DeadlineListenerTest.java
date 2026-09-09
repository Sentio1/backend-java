package com.lisovskyi.core_service.deadline_processor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.deadline.finder.DeadlineFinder;
import com.lisovskyi.core_service.holiday.dto.event.HolidayChangedEvent;
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
 * wrong on its own: which CaseEvents get looked up for the changed date, and that recalculation
 * runs with changedBy = null (no human is responsible for this particular dueOn shift - see the
 * comment on the class).
 */
@ExtendWith(MockitoExtension.class)
class DeadlineListenerTest {

    @Mock
    private DeadlineGenerator deadlineGenerator;

    @Mock
    private DeadlineFinder deadlineFinder;

    @InjectMocks
    private DeadlineListener deadlineListener;

    @Test
    void onHolidayChanged_looksUpCaseEventsForTheChangedDate_andRecalculatesWithNoChangedByUser() {
        LocalDate changedDate = LocalDate.of(2026, 11, 14);
        CaseEvent caseEvent = CaseEvent.builder().build();
        when(deadlineFinder.findTriggeringEventsOfPendingDeadlinesCovering(changedDate)).thenReturn(List.of(caseEvent));

        deadlineListener.onHolidayChanged(new HolidayChangedEvent(changedDate));

        verify(deadlineGenerator).recalcAllDeadlines(eq(List.of(caseEvent)), isNull());
    }

    @Test
    void onHolidayChanged_noAffectedDeadlines_stillCallsRecalcAllDeadlines_withEmptyList() {
        // DeadlineGenerator.recalcAllDeadlines сам коротко замикається на порожньому списку -
        // цей тест лише фіксує, що DeadlineListener не намагається "оптимізувати" це сам і не
        // пропускає виклик.
        LocalDate changedDate = LocalDate.of(2026, 11, 14);
        when(deadlineFinder.findTriggeringEventsOfPendingDeadlinesCovering(changedDate)).thenReturn(List.of());

        deadlineListener.onHolidayChanged(new HolidayChangedEvent(changedDate));

        verify(deadlineGenerator).recalcAllDeadlines(eq(List.of()), isNull());
    }
}
