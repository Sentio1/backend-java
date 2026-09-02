package com.lisovskyi.core_service.deadline_processor;

import com.lisovskyi.core_service.case_event.CaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeadlineGenerator {

    private final DeadlineEngine deadlineEngine;

    public void recalcAllDeadlines(List<CaseEvent> caseEvents, Long changedBy) {
        if (caseEvents.isEmpty()) {
            log.info("No case events to recalculate");
            return;
        }

        log.info("Recalculating {} deadlines", caseEvents.size());
        caseEvents.forEach(caseEvent -> deadlineEngine.generateDeadline(caseEvent, changedBy));
    }
}
