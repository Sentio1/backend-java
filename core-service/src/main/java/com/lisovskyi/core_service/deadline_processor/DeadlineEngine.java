package com.lisovskyi.core_service.deadline_processor;

import com.lisovskyi.core_service.audit_log.AuditLogService;
import com.lisovskyi.core_service.audit_log.EntityType;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.CaseEvent;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.EventDateResolver;
import com.lisovskyi.core_service.deadline.Deadline;
import com.lisovskyi.core_service.deadline.DeadlineRepository;
import com.lisovskyi.core_service.deadline.WorkingDayCalculator;
import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.finder.DeadlineRuleFinder;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import com.lisovskyi.core_service.holiday.Holiday;
import com.lisovskyi.core_service.holiday.finder.HolidayFinder;
import com.lisovskyi.core_service.deadline.finder.DeadlineFinder;
import com.sentio.shared.entity.id.deadline.DeadlineId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeadlineEngine {

    private final DeadlineRepository deadlineRepository;
    private final DeadlineFinder deadlineFinder;
    private final DeadlineRuleFinder deadlineRuleFinder;
    private final HolidayFinder holidayFinder;
    private final AuditLogService auditLogService;


    // changedBy: null означає "немає людини-автора" (напр. автоматична реєстрація події з
    // Registry Monitor через registerRegistryCaseEvent - там ще нема SERVICE-токена з
    // ідентичністю виклику, див. README/SEN-33) - у цьому випадку зміну dueOn просто не
    // аудитуємо, а не вигадуємо фіктивного "системного" користувача, якого AuditLog.changedBy
    // (NOT NULL, soft-ref на реального auth.users.id) не мав би сенсу представляти.
    @Transactional
    public Long generateDeadline(CaseEvent caseEvent, Long changedBy) {
        Court court = caseEvent.getCase_().getCourt();
        if (court == null) {
            log.info("Skipping deadline calculation for event id={}: case has no court", caseEvent.getId());
            return null;
        }

        LocalDate occurredAt = EventDateResolver.toLocalDate(caseEvent.getOccurredAt(), court);

        ProcedureType procedure = caseEvent.getCase_().getProcedure();
        EventCode triggerEventCode = caseEvent.getEventCode();

        Optional<DeadlineRule> deadlineRuleOpt =
                deadlineRuleFinder.findByActiveRule(procedure, triggerEventCode, occurredAt);
        if (deadlineRuleOpt.isEmpty()) {
            // не помилка, просто нема правила
            return null;
        }

        DeadlineRule rule = deadlineRuleOpt.get();

        LocalDate startsOn =
                switch (rule.getCountFrom()) {
                    case CountFrom.NEXT_DAY -> occurredAt.plusDays(1);
                    case CountFrom.SAME_DAY -> occurredAt;
                };

        LocalDate dueOn =
                switch (rule.getDurationUnit()) {
                    case DurationUnit.DAY ->
                            switch (rule.getDayKind()) {
                                case CALENDAR -> startsOn.plusDays(rule.getDurationValue());
                                case WORKING -> {
                                    short upperBound = (short) (rule.getDurationValue() * 2 + 10);
                                    LocalDate upperBoundDate = startsOn.plusDays(upperBound);

                                    List<Holiday> holidays =
                                            holidayFinder.findAllByDateBetween(startsOn, upperBoundDate);
                                    Map<LocalDate, Boolean> holidayOverrides = new HashMap<>();
                                    holidays.forEach(
                                            holiday -> holidayOverrides.put(holiday.getDate(), holiday.isWorking()));

                                    yield WorkingDayCalculator.calculateDueOn(
                                            startsOn, rule.getDurationValue(), upperBoundDate, holidayOverrides);
                                }
                            };
                    case DurationUnit.MONTH -> startsOn.plusMonths(rule.getDurationValue());
                };

        // За triggeringEvent, а не за парою (triggeringEvent, rule): при зміні procedure/instance
        // справи (SEN-21) підбирається інше правило (інший рядок DeadlineRule), і пошук саме по
        // старому rule ніколи б не знайшов уже наявний дедлайн - замість оновлення на місці
        // з'являвся б другий, дублюючий рядок Deadline для тієї самої події.
        Optional<Deadline> existingOpt = deadlineFinder.findByTriggeringEvent(caseEvent);
        LocalDate oldDueOn = existingOpt.map(Deadline::getDueOn).orElse(null);

        Deadline deadline = existingOpt
                .map(existing -> {
                    log.info(
                            "Recalculating deadline id={} for event id={}: dueOn {} -> {}",
                            existing.getId(),
                            caseEvent.getId(),
                            existing.getDueOn(),
                            dueOn);

                    existing.setRule(rule);
                    existing.setTitle(rule.getTitle());
                    existing.setLegalBasis(rule.getLegalBasis());
                    existing.setStartsOn(startsOn);
                    existing.setDueOn(dueOn);
                    return existing;
                })
                .orElseGet(() -> Deadline.builder()
                        .title(rule.getTitle())
                        .legalBasis(rule.getLegalBasis())
                        .organizationId(caseEvent.getOrganizationId())
                        .case_(caseEvent.getCase_())
                        .rule(rule)
                        .triggeringEvent(caseEvent)
                        .startsOn(startsOn)
                        .dueOn(dueOn)
                        .build());

        Deadline savedDeadline = deadlineRepository.save(deadline);

        // Лише перерахунок УЖЕ наявного дедлайна (не первинне створення - там нема з чим
        // порівнювати "стару" дату, як і CaseService.updateCase не пише аудит на createCase),
        // і лише коли dueOn реально змінився, і лише коли є хто в цьому винний (changedBy != null).
        if (changedBy != null && oldDueOn != null && !Objects.equals(oldDueOn, dueOn)) {
            auditLogService.log(
                    OrganizationId.of(caseEvent.getOrganizationId()),
                    EntityType.DEADLINE,
                    DeadlineId.of(savedDeadline.getId()),
                    UserId.of(changedBy),
                    "dueOn",
                    oldDueOn.toString(),
                    dueOn.toString());
        }

        return savedDeadline.getId();
    }
}
