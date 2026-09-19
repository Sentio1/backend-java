package com.sentio.core_service.deadline.internal.engine;

import com.sentio.core_service.calendar.api.event.HolidayChangedEvent;
import com.sentio.core_service.deadline.internal.enums.DeadlineStatus;
import com.sentio.core_service.deadline.internal.repository.DeadlineRepository;
import com.sentio.core_service.litigation.api.dto.TriggeringEvent;
import com.sentio.core_service.litigation.api.service.CaseEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// SEN-26: перерахунок PENDING дедлайнів, чиє вікно накриває змінену дату - AFTER_COMMIT, щоб
// не реагувати на зміну holidays, яка потім відкотиться, і не бачити її мідтранзакційно.
// changedBy = null: тут немає людини, відповідальної саме за це зрушення dueOn - редактор
// календаря виправив факт, а не ухвалив рішення по конкретній справі (той самий підхід, що й
// автоматична реєстрація подій з Registry Monitor). DeadlineEngine записує це в аудит з
// ChangedByType.SYSTEM, а не пропускає запис.
@Component
@Slf4j
@RequiredArgsConstructor
public class DeadlineListener {

    private final DeadlineRepository deadlineRepository;
    private final CaseEventService caseEventService;
    private final DeadlineEngine deadlineEngine;

    // REQUIRES_NEW: in AFTER_COMMIT the original transaction is already committed but still bound
    // to the thread - a plain @Transactional (REQUIRED) further down would "join" it and its
    // writes would never be committed. The recalculation needs its own transaction.
    public void onHolidayChanged(HolidayChangedEvent event) {
        List<Long> caseEventIds = deadlineRepository.findTriggeringEventIdsByStatusAndWindowCovering(
                DeadlineStatus.PENDING, event.date());
        if (caseEventIds.isEmpty()) {
            log.info("Holiday {} changed - no pending deadlines to recalculate", event.date());
            return;
        }

        List<TriggeringEvent> caseEvents = caseEventService.findTriggeringEvents(caseEventIds);
        log.info("Holiday {} changed - recalculating deadlines of {} case event(s)", event.date(), caseEvents.size());
        caseEvents.forEach(caseEvent -> deadlineEngine.generateDeadline(caseEvent, null));
    }
}
