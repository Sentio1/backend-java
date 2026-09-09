package com.lisovskyi.core_service.deadline_processor;

import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.deadline.finder.DeadlineFinder;
import com.lisovskyi.core_service.holiday.dto.event.HolidayChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

// SEN-26: перерахунок PENDING дедлайнів, чиє вікно накриває змінену дату - AFTER_COMMIT, щоб
// не реагувати на зміну holidays, яка потім відкотиться, і не бачити її мідтранзакційно.
// changedBy = null: тут немає людини, відповідальної саме за це зрушення dueOn - редактор
// календаря виправив факт, а не ухвалив рішення по конкретній справі (той самий підхід, що й
// автоматична реєстрація подій з Registry Monitor у DeadlineEngine). DeadlineEngine записує це
// в аудит з ChangedByType.SYSTEM, а не пропускає запис.
@Component
@Slf4j
@RequiredArgsConstructor
public class DeadlineListener {

    private final DeadlineGenerator deadlineGenerator;
    private final DeadlineFinder deadlineFinder;

    // Якщо транзакція відкатується, то не потрібно перераховувати дедлайни.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHolidayChanged(HolidayChangedEvent event) {
        List<CaseEvent> caseEvents = deadlineFinder.findTriggeringEventsOfPendingDeadlinesCovering(event.date());
        deadlineGenerator.recalcAllDeadlines(caseEvents, null);
    }
}
